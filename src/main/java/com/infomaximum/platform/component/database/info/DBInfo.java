package com.infomaximum.platform.component.database.info;

import com.infomaximum.database.exception.DatabaseException;
import com.infomaximum.database.provider.DBProvider;
import com.infomaximum.database.provider.DBTransaction;
import com.infomaximum.database.utils.TypeConvert;
import com.infomaximum.platform.exception.PlatformException;
import com.infomaximum.platform.sdk.exception.GeneralExceptionBuilder;
import com.infomaximum.platform.utils.JsonUtils;
import net.minidev.json.JSONObject;

import java.util.Objects;
import java.util.UUID;

import static com.infomaximum.database.schema.Schema.SERVICE_COLUMN_FAMILY;

public class DBInfo {

    private static final int DEFAULT_VERSION = 1;
    private static final byte[] INFO_KEY = TypeConvert.pack("info");
    private static final String NODE = "node";
    private static final String VERSION = "version";
    private static final String GUID = "guid";

    private static DBInfo cachedDbInfo;

    private final String guid;
    private final int version;

    private DBInfo(String guid, int version) {
        this.guid = guid;
        this.version = version;
    }

    public static DBInfo createIfNotExists(DBProvider dbProvider) throws PlatformException {
        if (!dbProvider.containsColumnFamily(SERVICE_COLUMN_FAMILY)) {
            throw GeneralExceptionBuilder.buildIllegalStateException("Not created service_column_family");
        }
        String infoJson = TypeConvert.unpackString(dbProvider.getValue(SERVICE_COLUMN_FAMILY, INFO_KEY));
        if (Objects.nonNull(infoJson)) {
            return cachedDbInfo = fromJsonString(infoJson);
        }
        cachedDbInfo = new DBInfo(UUID.randomUUID().toString(), DEFAULT_VERSION);
        saveDbInfo(cachedDbInfo, dbProvider);
        return cachedDbInfo;
    }

    public static DBInfo get(DBProvider dbProvider) throws PlatformException {
        if (Objects.nonNull(cachedDbInfo)) {
            return cachedDbInfo;
        }
        String infoJson = TypeConvert.unpackString(dbProvider.getValue(SERVICE_COLUMN_FAMILY, INFO_KEY));
        if (Objects.nonNull(infoJson)) {
            return cachedDbInfo = fromJsonString(infoJson);
        }
        return cachedDbInfo;
    }

    public String toJsonString() {
        JSONObject nodeJson = new JSONObject();
        nodeJson.put(GUID, guid);
        JSONObject infoJson = new JSONObject();
        infoJson.put(NODE, nodeJson);
        infoJson.put(VERSION, version);
        return infoJson.toJSONString();
    }

    public String getGuid() {
        return guid;
    }

    public int getVersion() {
        return version;
    }

    private static DBInfo fromJsonString(String infoJsonStr) throws PlatformException {
        JSONObject infoJson = JsonUtils.parse(infoJsonStr, JSONObject.class);
        Integer version = JsonUtils.getValue(VERSION, Integer.class, infoJson);
        JSONObject nodeValue = JsonUtils.getValue(NODE, JSONObject.class, infoJson);
        String guid = JsonUtils.getValue(GUID, String.class, nodeValue);
        return new DBInfo(guid, version);
    }

    private static void saveDbInfo(DBInfo dbInfo, DBProvider dbProvider) throws DatabaseException {
        try (DBTransaction transaction = dbProvider.beginTransaction()) {
            transaction.put(SERVICE_COLUMN_FAMILY, INFO_KEY, TypeConvert.pack(dbInfo.toJsonString()));
            transaction.commit();
        }
    }
}
