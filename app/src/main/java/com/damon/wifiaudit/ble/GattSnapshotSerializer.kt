package com.damon.wifiaudit.ble

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object GattSnapshotSerializer {

    fun toJson(services: List<LightGattManager.BleService>): String {
        val root = JSONArray()
        services.forEach { svc ->
            val svcObj = JSONObject()
            svcObj.put("uuid", svc.uuid.toString())
            svcObj.put("name", svc.name)
            val chars = JSONArray()
            svc.characteristics.forEach { c ->
                val cObj = JSONObject()
                cObj.put("uuid", c.uuid.toString())
                cObj.put("name", c.name)
                cObj.put("properties", c.properties)
                cObj.put("serviceUuid", c.serviceUuid.toString())
                chars.put(cObj)
            }
            svcObj.put("characteristics", chars)
            root.put(svcObj)
        }
        return root.toString()
    }

    fun fromJson(json: String): List<LightGattManager.BleService> {
        val list = mutableListOf<LightGattManager.BleService>()
        val root = JSONArray(json)
        for (i in 0 until root.length()) {
            val svcObj = root.getJSONObject(i)
            val serviceUuid = UUID.fromString(svcObj.getString("uuid"))
            val chars = mutableListOf<LightGattManager.BleCharacteristic>()
            val charArr = svcObj.getJSONArray("characteristics")
            for (j in 0 until charArr.length()) {
                val cObj = charArr.getJSONObject(j)
                val charServiceUuid = if (cObj.has("serviceUuid")) {
                    UUID.fromString(cObj.getString("serviceUuid"))
                } else {
                    serviceUuid
                }
                chars.add(
                    LightGattManager.BleCharacteristic(
                        uuid = UUID.fromString(cObj.getString("uuid")),
                        name = if (cObj.isNull("name")) null else cObj.optString("name"),
                        properties = cObj.getInt("properties"),
                        serviceUuid = charServiceUuid
                    )
                )
            }
            list.add(
                LightGattManager.BleService(
                    uuid = serviceUuid,
                    name = if (svcObj.isNull("name")) null else svcObj.optString("name"),
                    characteristics = chars
                )
            )
        }
        return list
    }
}
