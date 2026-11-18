package com.example

import com.example.data.model.WeatherCondition
import com.example.data.remote.ResilientDns
import com.example.data.remote.SecondaryWeatherClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ResilientDnsAndMappingTest {

    @Test
    fun parseDohAnswerIps_extractsARecordsAndIgnoresCnames() {
        val sampleDohJson = """
            {
              "Status": 0,
              "Answer": [
                {"name": "api.open-meteo.com.", "type": 5, "TTL": 300, "data": "edge.open-meteo.com."},
                {"name": "edge.open-meteo.com.", "type": 1, "TTL": 300, "data": "94.130.142.35"},
                {"name": "edge.open-meteo.com.", "type": 1, "TTL": 300, "data": "116.202.238.151"}
              ]
            }
        """.trimIndent()

        val ips = ResilientDns.parseDohAnswerIps("api.open-meteo.com", sampleDohJson)
        assertEquals(2, ips.size)
        assertEquals("api.open-meteo.com", ips[0].hostName)
        assertEquals("94.130.142.35", ips[0].hostAddress)
        assertEquals("116.202.238.151", ips[1].hostAddress)
    }

    @Test
    fun mapMetSymbolToCondition_mapsCommonSymbolsAccurately() {
        assertEquals(WeatherCondition.CLEAR, SecondaryWeatherClient.mapMetSymbolToCondition("clearsky_day"))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, SecondaryWeatherClient.mapMetSymbolToCondition("partlycloudy_night"))
        assertEquals(WeatherCondition.OVERCAST, SecondaryWeatherClient.mapMetSymbolToCondition("cloudy"))
        assertEquals(WeatherCondition.RAIN, SecondaryWeatherClient.mapMetSymbolToCondition("heavyrain"))
        assertEquals(WeatherCondition.SNOW, SecondaryWeatherClient.mapMetSymbolToCondition("snowshowers_day"))
        assertEquals(WeatherCondition.THUNDERSTORM, SecondaryWeatherClient.mapMetSymbolToCondition("rainandthunder"))
        assertEquals(WeatherCondition.FOG, SecondaryWeatherClient.mapMetSymbolToCondition("fog"))
    }

    @Test
    fun buildAndParseUdpDnsPacket_extractsIpv4ARecord() {
        val query = ResilientDns.buildDnsQueryPacket("api.open-meteo.com", transactionId = 0x1234)
        // Construct a synthetic DNS response echoing the question + 1 A-record (94.130.142.35)
        val response = ByteArray(query.size + 16)
        System.arraycopy(query, 0, response, 0, query.size)
        response[2] = 0x81.toByte() // Standard response
        response[7] = 0x01 // ANCOUNT = 1
        var p = query.size
        response[p++] = 0xC0.toByte(); response[p++] = 0x0C // Pointer to QNAME
        response[p++] = 0x00; response[p++] = 0x01 // TYPE A
        response[p++] = 0x00; response[p++] = 0x01 // CLASS IN
        response[p++] = 0x00; response[p++] = 0x00; response[p++] = 0x01; response[p++] = 0x2C // TTL 300
        response[p++] = 0x00; response[p++] = 0x04 // RDLENGTH 4
        response[p++] = 94.toByte(); response[p++] = 130.toByte(); response[p++] = 142.toByte(); response[p++] = 35.toByte()

        val parsed = ResilientDns.parseDnsAResponse("api.open-meteo.com", response, response.size)
        assertEquals(1, parsed.size)
        assertEquals("94.130.142.35", parsed[0].hostAddress)
    }
}
