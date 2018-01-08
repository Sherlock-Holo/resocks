package resocks.websocket.http

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HttpHeaderTest {

    @Test
    fun getHeaderByteArray() {
        assertArrayEquals(
                "http header test failed!",
                ("HTTP/1.1 101 Switching Protocols\r\n" + "Upgrade: websocket\r\n" + "ClientConnection: Upgrade\r\n" +
                        "Sec-WebSocket-Accept: fFBooB7FAkLlXgRSz0BT3v4hq5s=\r\n\r\n").toByteArray(),
                HttpHeader.offerHttpHeader("sN9cRrP/n9NdMgdcy2VJFQ==").getHeaderByteArray()
        )
    }

    @Test
    fun getHeaderString() {
        assertEquals(
                "HTTP/1.1 101 Switching Protocols\r\n" + "Upgrade: websocket\r\n" + "ClientConnection: Upgrade\r\n" +
                        "Sec-WebSocket-Accept: fFBooB7FAkLlXgRSz0BT3v4hq5s=\r\n\r\n",
                HttpHeader.offerHttpHeader("sN9cRrP/n9NdMgdcy2VJFQ==").getHeaderString()
        )
    }

    @Test
    fun checkHttpHeader() {
        assertEquals(true, HttpHeader.offerHttpHeader().checkHttpHeader())
    }

    @Test
    fun checkHttpHeader1() {
        assertEquals(true, HttpHeader.offerHttpHeader().checkHttpHeader("sN9cRrP/n9NdMgdcy2VJFQ=="))
    }

    @Test
    fun offerHttpHeader() {
        assertEquals(
                "HTTP/1.1 101 Switching Protocols\r\n" + "Upgrade: websocket\r\n" + "ClientConnection: Upgrade\r\n" +
                        "Sec-WebSocket-Accept: fFBooB7FAkLlXgRSz0BT3v4hq5s=\r\n\r\n",
                HttpHeader.offerHttpHeader("sN9cRrP/n9NdMgdcy2VJFQ==").getHeaderString()
        )
    }

    @Test
    fun offerHttpHeader1() {
    }

    @Test
    fun genSecWebSocketAccept() {
        assertEquals("fFBooB7FAkLlXgRSz0BT3v4hq5s=", HttpHeader.genSecWebSocketAccept("sN9cRrP/n9NdMgdcy2VJFQ=="))
    }

    @Test
    fun getHttpHeader() {
    }
}