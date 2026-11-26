package com.digitalku.roost.remote

import com.digitalku.roost.remote.net.AddTags
import com.digitalku.roost.remote.net.AssetPage
import com.digitalku.roost.remote.net.CurrentLibrary
import com.digitalku.roost.remote.net.LocalAddress
import com.digitalku.roost.remote.net.ReadPage
import com.digitalku.roost.remote.net.Redrawn
import com.digitalku.roost.remote.net.RemoteException
import com.digitalku.roost.remote.net.Request
import com.digitalku.roost.remote.net.Server
import com.digitalku.roost.remote.net.ServerEvent
import com.digitalku.roost.remote.net.Session
import com.digitalku.roost.remote.net.SidebarRow
import com.digitalku.roost.remote.net.parseEvent
import com.digitalku.roost.remote.net.parseServerAddress
import com.digitalku.roost.remote.net.wire
import com.digitalku.roost.remote.ui.suggestions
import java.net.InetAddress
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProtocolTest {
    private fun local(text: String) = LocalAddress.isLocal(InetAddress.getByName(text))

    @Test
    fun `the addresses the server answers are the ones this app talks to`() {
        for (address in listOf("127.0.0.1", "10.1.2.3", "172.16.0.9", "172.31.255.1", "192.168.1.20",
            "169.254.3.4", "100.64.0.1", "100.127.255.254", "::1", "fe80::1", "fd7a:115c:a1e0::1")) {
            assertTrue(address, local(address))
        }
        for (address in listOf("8.8.8.8", "172.32.0.1", "100.63.255.255", "100.128.0.1",
            "2606:4700::1111")) {
            assertFalse(address, local(address))
        }
    }

    @Test
    fun `an address is read as the window prints it`() {
        assertEquals("http://192.168.1.20:7387/", parseServerAddress("192.168.1.20")?.toString())
        assertEquals("http://192.168.1.20:7387/", parseServerAddress(" http://192.168.1.20:7387/ ")?.toString())
        assertEquals("http://192.168.1.20:8000/", parseServerAddress("192.168.1.20:8000")?.toString())
        assertEquals("http://[fd7a::1]:7387/", parseServerAddress("[fd7a::1]")?.toString())
        assertEquals("http://laptop.tail1.ts.net:7387/", parseServerAddress("laptop.tail1.ts.net")?.toString())
        assertNull(parseServerAddress("https://192.168.1.20"))
        assertNull(parseServerAddress(""))
    }

    @Test
    fun `a request is spelled as the contract spells it`() {
        val page = wire.encodeToString(Request.serializer(), ReadPage("", 10, null, null))
        val parsed = Json.parseToJsonElement(page).jsonObject
        assertEquals("\"assets:page\"", parsed["type"].toString())
        // RR-8: a required field is sent, null included.
        assertEquals("null", parsed["after"].toString())
        assertEquals("null", parsed["sort"].toString())
        assertEquals(
            """{"type":"library:current"}""",
            wire.encodeToString(Request.serializer(), CurrentLibrary),
        )
        assertEquals(
            """{"type":"asset:tagAdd","ids":["A"],"tags":["x"]}""",
            wire.encodeToString(Request.serializer(), AddTags(listOf("A"), listOf("x"))),
        )
    }

    @Test
    fun `a thumbnail's address carries what changes it, in order`() {
        val session = Session(parseServerAddress("10.0.0.2")!!, "t", OkHttpClient())
        assertEquals("http://10.0.0.2:7387/bytes/thumb/ABC@256", session.thumbnail("ABC", null, null))
        assertEquals("http://10.0.0.2:7387/bytes/thumb/ABC@512?at=1500&v=99", session.thumbnail("ABC", 1500, 99, 512))
        assertEquals("http://10.0.0.2:7387/bytes/thumb/ABC@256?v=99", session.thumbnail("ABC", null, 99))
        assertEquals("http://10.0.0.2:7387/bytes/file/ABC?h=ff", session.file("ABC", "ff"))
    }

    @Test
    fun `events are read by name, and an unknown one is passed over`() {
        assertEquals(ServerEvent.Changed, parseEvent("library:changed", """{"type":"library:changed"}"""))
        assertEquals(ServerEvent.Renamed("rumah"), parseEvent("library:renamed", """{"type":"library:renamed","root":"/x","name":"rumah"}"""))
        assertEquals(
            ServerEvent.Derived(listOf(Redrawn("A", 5, null, false))),
            parseEvent("assets:derived", """{"type":"assets:derived","assets":[{"id":"A","derivedAt":5,"undrawn":null,"chosen":false}]}"""),
        )
        assertNull(parseEvent("library:folding", "{}"))
        assertNull(parseEvent(null, "{}"))
    }

    @Test
    fun `a tag is offered from the start of a word, commonest first`() {
        fun row(name: String, count: Long) = SidebarRow(name, name, "tag:$name", count, 0, null, null)
        val tags = listOf(row("olahraga", 3), row("sepak-bola", 9), row("bola", 1), row("laut", 4))
        assertEquals(listOf("sepak-bola", "bola"), suggestions(tags, emptyList(), "bol"))
        assertEquals(listOf("bola"), suggestions(tags, listOf("Sepak-Bola"), "bol"))
        assertEquals(emptyList<String>(), suggestions(tags, emptyList(), " "))
    }

    @Test
    fun `a request goes with the token and its value comes back`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().body(
                """{"ok":true,"value":{"assets":[],"next":null,"beneath":null}}""",
            ).build())
            server.start()
            val session = Session(server.url("/"), "secret", OkHttpClient())
            val page = session.ask(ReadPage("", 10, null, null), AssetPage.serializer())
            assertTrue(page.assets.isEmpty())
            val sent = server.takeRequest()
            assertEquals("/api/1/request", sent.url.encodedPath)
            assertEquals("Bearer secret", sent.headers["Authorization"])
        }
    }

    @Test
    fun `a refusal comes back as its code`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().code(403).body(
                """{"ok":false,"problem":{"code":"password_wrong","message":"no","detail":{}}}""",
            ).build())
            server.enqueue(MockResponse.Builder().body(
                """{"ok":false,"problem":{"code":"unknown_asset","message":"gone","detail":{"id":"A"}}}""",
            ).build())
            server.start()
            try {
                Server(server.url("/"), OkHttpClient()).pair("salah", "Pixel")
                fail("paired with a wrong password")
            } catch (e: RemoteException) {
                assertEquals(403, e.status)
                assertEquals("password_wrong", e.code)
            }
            try {
                Session(server.url("/"), "t", OkHttpClient()).ask(CurrentLibrary, JsonObject.serializer())
                fail("an engine's refusal was taken for a value")
            } catch (e: RemoteException) {
                assertEquals(200, e.status)
                assertEquals("unknown_asset", e.code)
            }
        }
    }
}
