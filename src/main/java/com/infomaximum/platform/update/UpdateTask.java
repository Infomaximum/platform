package com.infomaximum.platform.update;

import com.infomaximum.database.domainobject.Transaction;
import com.infomaximum.database.domainobject.filter.EmptyFilter;
import com.infomaximum.database.domainobject.iterator.IteratorEntity;
import com.infomaximum.database.exception.DatabaseException;
import com.infomaximum.database.schema.Schema;
import com.infomaximum.platform.Platform;
import com.infomaximum.platform.exception.DowngradingException;
import com.infomaximum.platform.sdk.component.Component;
import com.infomaximum.platform.sdk.component.Info;
import com.infomaximum.platform.sdk.component.version.Version;
import com.infomaximum.platform.sdk.domainobject.module.ModuleEditable;
import com.infomaximum.platform.sdk.domainobject.module.ModuleReadable;
import com.infomaximum.platform.update.annotation.Dependency;
import com.infomaximum.platform.update.annotation.Update;
import com.infomaximum.platform.update.exception.UpdateException;
import com.infomaximum.platform.update.util.UpdateUtil;
import com.infomaximum.subsystems.querypool.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class UpdateTask<T extends Component> {

    private final static Logger log = LoggerFactory.getLogger(UpdateTask.class);

    private T component;

    public UpdateTask(T component) {
        this.component = component;
    }

    public void execute(ModuleEditable moduleEditable, Transaction transaction) throws DatabaseException {
        validateUpdateTask(moduleEditable, transaction);
        updateComponent(transaction);
    }

    public Info getComponentInfo() {
        return (Info) component.getInfo();
    }

    @SuppressWarnings("unchecked")
    public void validateUpdateTask(ModuleEditable module, Transaction transaction) throws DatabaseException {
        Version lastModuleVersion = module.getVersion();
        Version currentCodeVersion = getComponentInfo().getVersion();

        final Update taskAnnotation = UpdateUtil.getUpdateAnnotation(this.getClass());
        Version previousTaskVersion = Version.parseTaskUpdate(taskAnnotation.previousVersion());
        Version nextTaskVersion = Version.parseTaskUpdate(taskAnnotation.version());

        //??????????????????, ?????? UpdateTask ?????????? ?????????????????????? ?? ?????????????????????????? ????????????
        if (compareWithIgnorePatch(lastModuleVersion, previousTaskVersion) != 0) {
            throw new UpdateException(getComponentInfo().getUuid(), "Previous module version: " + lastModuleVersion + " doesn't equal to update task previous version: " + previousTaskVersion);
        }
        //??????????????????, ?????? ?????????????? ?????????????? ???????? ???????????? ???????????? ?????????? ??????(UpdateTask) ???? ?????????????? ?????????????????????? - ??.??. ?????? ???? ???????????? ?????????? ????????????
        if (compareWithIgnorePatch(currentCodeVersion, nextTaskVersion) != 0) {
            throw new UpdateException(getComponentInfo().getUuid(), "Current code version " + currentCodeVersion + " doesn't equal to update task next version" + nextTaskVersion);
        }
        int cmpResult = compareWithIgnorePatch(nextTaskVersion, previousTaskVersion);
        if (cmpResult < 0) {
            throw new DowngradingException(getComponentInfo().getUuid(), nextTaskVersion, previousTaskVersion);
        }
        String subsystemUuid = module.getUuid();
        if (Arrays.stream(taskAnnotation.dependencies()).anyMatch(d -> d.componentUUID().equals(subsystemUuid))) {
            throw new UpdateException(getComponentInfo().getUuid(), "Incorrect dependency. Update with self dependence doesn't allow");
        }
        validateUpdateDependencies(taskAnnotation, transaction);
    }

    /**
     * ?????????????? ???????????? ?????? ???????????????????? ?????????????????? ????????
     * @param left
     * @param right
     * @return
     */
    private static int compareWithIgnorePatch(Version left, Version right) {
        if (left.product != right.product) {
            return Integer.compare(left.product, right.product);
        }
        if (left.major != right.major) {
            return Integer.compare(left.major, right.major);
        }
        if (left.minor != right.minor) {
            return Integer.compare(left.minor, right.minor);
        }
        return 0;
    }

    private void validateUpdateDependencies(Update taskAnnotation, Transaction transaction) throws DatabaseException {
        List<ModuleReadable> modules = getModules(transaction);
        log.warn(modules.toString());
        Dependency[] dependencies = taskAnnotation.dependencies();
        log.warn(Arrays.toString(dependencies));
        for (Dependency dependency : dependencies) {
            validateUpdateDependence(dependency, modules);
        }
    }

    private void validateUpdateDependence(Dependency dependency, List<ModuleReadable> modules) throws DatabaseException {
        ModuleReadable dependenceModule = null;
        for (ModuleReadable module : modules) {
            if (module.getUuid().equals(dependency.componentUUID())) {
                dependenceModule = module;
                break;
            }
        }
        if (dependenceModule == null) {
            throw new UpdateException(getComponentInfo().getUuid(), "Can't find dependence module in system " + dependency.componentUUID());
        }
        Version expectedDependenceModule = Version.parseWithMigration(dependency.version());
        if (!dependenceModule.getVersion().equals(expectedDependenceModule)) {
            throw new UpdateException(getComponentInfo().getUuid(), "Wrong dependence module version. Current version: " + dependenceModule.getVersion() + ", expected: " + expectedDependenceModule
                    + ". Dependence on module: " + dependency.componentUUID());
        }
    }

    private List<ModuleReadable> getModules(Transaction transaction) throws DatabaseException {
        List<ModuleReadable> result = new ArrayList<>();
        try (IteratorEntity<ModuleReadable> iter = transaction.find(ModuleReadable.class, EmptyFilter.INSTANCE)) {
            while (iter.hasNext()) {
                result.add(iter.next());
            }
        }
        return result;
    }

    protected Schema getSchema(Transaction transaction) throws DatabaseException {
        return Schema.read(transaction.getDbProvider());
    }

    protected <Y> Y executeQuery(Query<Y> query) {
        try {
            return Platform.get().getQueryPool().execute(
                    component,
                    query
            ).get();
        } catch (Throwable e) {
            throw new UpdateException(e);
        }
    }

    protected abstract void updateComponent(Transaction transaction) throws DatabaseException;
}
