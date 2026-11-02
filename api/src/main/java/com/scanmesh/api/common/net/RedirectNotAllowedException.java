package com.scanmesh.api.common.net;

import java.io.IOException;
import java.net.URI;

/** 다른 호스트로 리다이렉트하거나 리다이렉트가 너무 많아서 따라가지 않았다 */
public class RedirectNotAllowedException extends IOException {

    public RedirectNotAllowedException(URI from, String location) {
        super(from + " -> " + location);
    }
}
