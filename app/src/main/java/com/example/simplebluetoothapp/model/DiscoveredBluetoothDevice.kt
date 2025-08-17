package com.example.simplebluetoothapp.model

data class DiscoveredBluetoothDevice(
    val name: String?,
    val address: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DiscoveredBluetoothDevice
        if (address != other.address) return false
        return true
    }

    override fun hashCode(): Int {
        return address.hashCode()
    }
}