package com.infomaximum.platform.update.core;

import com.infomaximum.cluster.struct.Info;
import com.infomaximum.cluster.struct.Version;
import com.infomaximum.database.anotation.Entity;
import com.infomaximum.database.domainobject.Transaction;
import com.infomaximum.database.domainobject.filter.HashFilter;
import com.infomaximum.database.domainobject.iterator.IteratorEntity;
import com.infomaximum.database.exception.DatabaseException;
import com.infomaximum.database.schema.Schema;
import com.infomaximum.database.schema.StructEntity;
import com.infomaximum.platform.sdk.component.Component;
import com.infomaximum.platform.sdk.domainobject.module.ModuleEditable;
import com.infomaximum.platform.update.UpdateTask;
import com.infomaximum.platform.update.util.UpdateUtil;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class UpdateService {

    private final static Logger log = LoggerFactory.getLogger(UpdateService.class);

    /**
     * Обновляет компоненты с прогресс-уведомлением.
     *
     * @param onModuleUpdated вызывается после каждого {@link #updateComponent}; может быть
     *     {@code null}, если вызывающему прогресс не нужен
     */
    public static void updateComponents(Transaction transaction,
                                        Runnable onModuleUpdated,
                                        ModuleUpdateEntity... updates) throws DatabaseException {
        Schema.resolve(ModuleEditable.class); //todo V.Bukharkin вынести отсюда
        List<UpdateUtil.ModuleTaskUpdate> moduleTaskUpdates = UpdateUtil.getUpdatesInCorrectOrder(updates);
        for (UpdateUtil.ModuleTaskUpdate moduleTaskUpdate : moduleTaskUpdates) {
            updateComponent(moduleTaskUpdate, transaction);
            if (onModuleUpdated != null) {
                onModuleUpdated.run();
            }
        }
    }

    public static void beforeUpdateComponents(Transaction transaction,
                                              ModuleUpdateEntity... updates) throws DatabaseException {
        Schema.resolve(ModuleEditable.class); //todo V.Bukharkin вынести отсюда
        List<UpdateUtil.ModuleTaskUpdate> moduleTaskUpdates = UpdateUtil.getUpdatesInCorrectOrder(updates);
        for (UpdateUtil.ModuleTaskUpdate moduleTaskUpdate : moduleTaskUpdates) {
            beforeUpdateComponent(moduleTaskUpdate, transaction);
        }
    }

    private static void beforeUpdateComponent(UpdateUtil.ModuleTaskUpdate moduleTaskUpdate,
                                              Transaction transaction) throws DatabaseException {
        Info componentInfo = moduleTaskUpdate.getComponent().getInfo();

        try (IteratorEntity<ModuleEditable> iter = transaction.find(ModuleEditable.class, new HashFilter(ModuleEditable.FIELD_UUID, componentInfo.getUuid()))) {
            if (iter.hasNext()) {
                ModuleEditable moduleEditable = iter.next();
                log.info("Before updating subsystem: " + componentInfo.getUuid() + ". From version " + moduleEditable.getVersion() + " to version " + componentInfo.getVersion());
                UpdateTask<? extends Component> updateTask = moduleTaskUpdate.getUpdateTask();
                if (updateTask != null) {
                    updateTask.executeBeforeUpdate(moduleEditable, transaction);
                }
            }
        }
    }

    private static void updateComponent(UpdateUtil.ModuleTaskUpdate moduleTaskUpdate,
                                        Transaction transaction) throws DatabaseException {
        Info componentInfo = moduleTaskUpdate.getComponent().getInfo();

        try (IteratorEntity<ModuleEditable> iter = transaction.find(ModuleEditable.class, new HashFilter(ModuleEditable.FIELD_UUID, componentInfo.getUuid()))) {
            if (iter.hasNext()) {
                ModuleEditable moduleEditable = iter.next();
                log.info("Updating subsystem: " + componentInfo.getUuid() + ". From version " + moduleEditable.getVersion() + " to version " + componentInfo.getVersion());

                UpdateTask<? extends Component> updateTask = moduleTaskUpdate.getUpdateTask();
                if (updateTask != null) {
                    updateTask.execute(transaction);
                }

                //Сохраняем
                Version codeVersion = componentInfo.getVersion();
                moduleEditable.setVersion(codeVersion);
                transaction.save(moduleEditable);
            }
        }
        Set<StructEntity> domains = new HashSet<>();
        for (Class domainObjectClass : new Reflections(componentInfo.getUuid()).getTypesAnnotatedWith(Entity.class, true)) {
            domains.add(Schema.getEntity(domainObjectClass));
        }
        Schema.read(transaction.getDbProvider()).checkSubsystemIntegrity(domains, componentInfo.getUuid());
    }
}
