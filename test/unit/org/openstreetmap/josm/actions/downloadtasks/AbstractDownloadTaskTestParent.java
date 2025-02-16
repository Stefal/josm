// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions.downloadtasks;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import org.junit.jupiter.api.BeforeEach;
import org.openstreetmap.josm.testutils.annotations.BasicWiremock;
import org.openstreetmap.josm.testutils.annotations.HTTPS;

/**
 * Superclass of {@link DownloadGpsTaskTest}, {@link DownloadOsmTaskTest} and {@link DownloadNotesTaskTest}.
 */
@BasicWiremock
@HTTPS
public abstract class AbstractDownloadTaskTestParent {
    WireMockRuntimeInfo wireMockServer;
    @BeforeEach
    void setup(WireMockRuntimeInfo wireMockRuntimeInfo) {
        this.wireMockServer = wireMockRuntimeInfo;
    }

    /**
     * Returns the path to remote test file to download via http.
     * @return the path to remote test file, relative to JOSM root directory
     */
    protected abstract String getRemoteFile();

    /**
     * Returns the {@code Content-Type} with which to serve the file referenced
     * by {@link #getRemoteFile()}
     * @return the {@code Content-Type} string for file {@link #getRemoteFile()}
     */
    protected String getRemoteContentType() {
        return "text/xml";
    }

    /**
     * Returns the http URL to remote test file to download.
     * @return the http URL to remote test file to download
     */
    protected final String getRemoteFileUrl() {
        final String remote = getRemoteFile();
        if (remote.startsWith("/")) {
            return wireMockServer.getHttpBaseUrl() + getRemoteFile();
        }
        return wireMockServer.getHttpBaseUrl() + '/' + getRemoteFile();
    }

    /**
     * Mock the HTTP server.
     */
    protected final void mockHttp() {
        wireMockServer.getWireMock().register(get(urlEqualTo("/" + getRemoteFile()))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", getRemoteContentType())
                    .withBodyFile(getRemoteFile())));
    }
}
