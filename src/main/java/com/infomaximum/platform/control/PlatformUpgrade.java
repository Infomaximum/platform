package com.infomaximum.platform.control;

import com.infomaximum.database.anotation.Entity;
import com.infomaximum.database.domainobject.DomainObject;
import com.infomaximum.database.domainobject.DomainObjectSource;
import com.infomaximum.database.domainobject.Transaction;
import com.infomaximum.database.domainobject.filter.EmptyFilter;
import com.infomaximum.database.domainobject.filter.HashFilter;
import com.infomaximum.database.domainobject.iterator.IteratorEntity;
import com.infomaximum.database.exception.DatabaseException;
import com.infomaximum.database.exception.SchemaException;
import com.infomaximum.database.maintenance.SchemaService;
import com.infomaximum.database.provider.DBProvider;
import com.infomaximum.database.schema.Schema;
import com.infomaximum.database.schema.StructEntity;
import com.infomaximum.platform.Platform;
import com.infomaximum.platform.component.database.DatabaseComponent;
import com.infomaximum.platform.exception.DowngradingException;
import com.infomaximum.platform.sdk.component.Component;
import com.infomaximum.platform.sdk.component.Info;
import com.infomaximum.platform.sdk.component.version.Version;
import com.infomaximum.platform.sdk.domainobject.module.ModuleEditable;
import com.infomaximum.platform.sdk.domainobject.module.ModuleReadable;
import com.infomaximum.platform.sdk.exception.GeneralExceptionBuilder;
import com.infomaximum.platform.update.core.ModuleUpdateEntity;
import com.infomaximum.platform.update.core.UpdateService;
import com.infomaximum.platform.update.core.UpgradeAction;
import com.infomaximum.subsystems.exception.SubsystemException;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PlatformUpgrade {

	private final static Logger log = LoggerFactory.getLogger(PlatformUpgrade.class);

	private final Platform platform;

	public PlatformUpgrade(Platform platform) {
		this.platform = platform;
	}

	public void install() throws SubsystemException {
		try {
			DatabaseComponent databaseSubsystem = platform.getCluster().getAnyLocalComponent(DatabaseComponent.class);
            databaseSubsystem.initialize();
			DBProvider provider = databaseSubsystem.getRocksDBProvider();
			Schema schema = Schema.read(provider);

			new DomainObjectSource(databaseSubsystem.getRocksDBProvider()).executeTransactional(transaction -> {
				schema.createTable(new StructEntity(ModuleReadable.class));

				//Регистрируем и установливаем модули
                List<Component> components = platform.getCluster().getDependencyOrderedComponentsOf(Component.class);
                for (Component component : components) {
                    installComponent(component, transaction);
                }
			});
		} catch (DatabaseException e) {
			throw GeneralExceptionBuilder.buildDatabaseException(e);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}


	public void upgrade() throws Exception {
//		DatabaseComponent databaseSubsystem = platform.getCluster().getAnyComponent(DatabaseComponent.class);
//		DBProvider provider = databaseSubsystem.getRocksDBProvider();
//		Schema schema = Schema.read(provider);

		List<Component> modules = platform.getCluster().getDependencyOrderedComponentsOf(Component.class);
		//Грузим сперва DatabaseSubsystem
		DatabaseComponent databaseComponent = platform.getCluster().getAnyLocalComponent(DatabaseComponent.class);
		databaseComponent.initialize();
		log.info("Database initialized...");

		new DomainObjectSource(databaseComponent.getRocksDBProvider()).executeTransactional(transaction -> {
			ensureSchema(transaction.getDbProvider());
			updateInstallModules(modules, transaction);
			removeRedundantModules(modules, transaction);

			//После обновления - перечитываем схему
			for (Component component : platform.getCluster().getDependencyOrderedComponentsOf(Component.class)) {
				component.reloadSchema(transaction.getDbProvider());
			}
		});

		new PlatformStartStop(platform).start(true);
		new PlatformStartStop(platform).stop();
	}

	private void ensureSchema(DBProvider dbProvider) throws DatabaseException {
		if (!Schema.exists(dbProvider)) {
			Schema.create(dbProvider);
		}
	}

	//TODO Ulitin V. Временно сделано public - перевести на private
	public void installComponent(Component component, Transaction transaction) throws DatabaseException {
		if (!(component.getInfo() instanceof Info)) {
			return;
		}

		Info info = (Info) component.getInfo();
		if (info.getVersion() == null) {
			return;
		}

		//Регистрируем компонент
		ModuleEditable moduleEditable = transaction.create(ModuleEditable.class);
		moduleEditable.setUuid(info.getUuid());
		moduleEditable.setVersion(info.getVersion());
		transaction.save(moduleEditable);

		//Создаем доменные сущности
	    Set<Class<? extends DomainObject>> objects = new HashSet<>();
        for (Class domainObjectClass : new Reflections(info.getUuid()).getTypesAnnotatedWith(Entity.class, true)) {
            objects.add(domainObjectClass);
        }
        try {
            SchemaService.install(objects, transaction.getDbProvider());
        } catch (DatabaseException e) {
            throw new SchemaException(e);
        }
	}

	private void updateInstallModules(List<Component> modules, Transaction transaction) throws Exception {
		Schema.resolve(ModuleReadable.class);

		List<ModuleUpdateEntity> modulesForUpdate = new ArrayList<>();
		for (Component module : modules) {
			ModuleEditable moduleInDB = getModuleByUuid(module.getInfo().getUuid(), transaction);
			UpgradeAction upgradeAction = getUpgradeAction(module, moduleInDB);
			switch (upgradeAction) {
				case NONE:
					log.warn("Module " + module.getInfo().getUuid() + " has actual version");
					break;
				case INSTALL:
					log.warn("Module " + module.getInfo().getUuid() + " installing");
					new PlatformUpgrade(platform).installComponent(module, transaction);
					break;
				case UPDATE:
					log.warn("Module " + module.getInfo().getUuid() + " ready for update");
					ModuleUpdateEntity updateEntity = new ModuleUpdateEntity(moduleInDB.getVersion(),
							((com.infomaximum.platform.sdk.component.Info)module.getInfo()).getVersion(),
							module.getInfo().getUuid());
					updateEntity.setComponent(module);
					modulesForUpdate.add(updateEntity);
					break;
			}
		}
		for (Component module : modules) {
			module.initialize();
		}
		update(modulesForUpdate, transaction);
	}

	public void update(List<ModuleUpdateEntity> updates, Transaction transaction) throws Exception {
		log.warn("Updating versions: " + updates);
		if (updates == null || updates.size() == 0) {
			return;
		}
		UpdateService.updateComponents(transaction, updates.toArray(new ModuleUpdateEntity[0]));
	}

	private ModuleEditable getModuleByUuid(String uuid, Transaction transaction) throws DatabaseException {
		try (IteratorEntity<ModuleEditable> iter = transaction.find(ModuleEditable.class, new HashFilter(ModuleEditable.FIELD_UUID, uuid))) {
			if (iter.hasNext()) {
				return iter.next();
			} else {
				return null;
			}
		}
	}

	private UpgradeAction getUpgradeAction(Component module, ModuleEditable moduleEditable) throws DatabaseException {
		if (moduleEditable == null) {
			return UpgradeAction.INSTALL;
		}
		Version previousVersion = moduleEditable.getVersion();
		Version nextVersion = ((com.infomaximum.platform.sdk.component.Info)module.getInfo()).getVersion();
		int cmpResult = Version.compare(nextVersion, previousVersion);
		if (cmpResult == 0) {
			return UpgradeAction.NONE;
		} else if (cmpResult > 0) {
			return UpgradeAction.UPDATE;
		} else {
			throw new DowngradingException(module.getInfo().getUuid(), nextVersion, previousVersion);
		}
	}

	private void removeRedundantModules(List<Component> modules, Transaction transaction) throws DatabaseException {
		Schema schema = Schema.read(transaction.getDbProvider());
		log.info("Checking for unused modules...");
		Set<String> moduleUuids = modules.stream().map(Component::getInfo).map(com.infomaximum.cluster.struct.Info::getUuid).collect(Collectors.toSet());
		log.warn("Debug: " + moduleUuids);
		try (IteratorEntity<ModuleEditable> mi = transaction.find(ModuleEditable.class, EmptyFilter.INSTANCE)) {
			while (mi.hasNext()) {
				ModuleEditable moduleEditable = mi.next();
				log.warn("Debug: " + moduleEditable.getUuid() + " Version: " + moduleEditable.getVersion());
				if (!moduleUuids.contains(moduleEditable.getUuid())
						//TODO Ulitin V. Удалить эту доп. условие после 01.01.2021
//						&& !moduleEditable.getUuid().equals(Subsystems.UUID)
				) {
					removeModule(moduleEditable, schema, transaction);
				}
			}
		}
	}

	private void removeModule(ModuleEditable module, Schema schema, Transaction transaction) throws DatabaseException {
		log.warn("Removing module " + module.getUuid() + "...");
		transaction.remove(module);
		schema.dropTablesByNamespace(module.getUuid());
	}
}
