package com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback

import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.StreamItem
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class MegaPlaybackTokenTest {
    private val stream = StreamItem(title = "CineSrc - Mega", url = "https://cdn.example/pl/opaque/master.m3u8?token=server", quality = "Mega HLS", provider = "CineSrc")

    @Test fun refreshOnlyDirectMegaStreams() {
        assertTrue(MegaPlaybackToken.needsRefresh(stream))
        assertFalse(MegaPlaybackToken.needsRefresh(stream.copy(provider = "Vidfast")))
        assertFalse(MegaPlaybackToken.needsRefresh(stream.copy(url = "https://api.example/m3u8-proxy?url=" + stream.url)))
        assertFalse(MegaPlaybackToken.needsRefresh(stream.copy(title = "CineSrc cld", quality = "Auto")))
    }

    @Test fun replaceAllOldTokensAndKeepSignedParameters() {
        val result = MegaPlaybackToken.replaceToken("https://cdn.example/pl/a/master.m3u8?sig=a%2Bb%3D&token=old&token=older", "fresh.token.signature").toHttpUrl()
        assertEquals(listOf("fresh.token.signature"), result.queryParameterValues("token"))
        assertEquals("a+b=", result.queryParameter("sig"))
        assertEquals("/pl/a/master.m3u8", result.encodedPath)
    }
}
