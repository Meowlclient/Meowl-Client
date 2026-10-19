package com.lennart.nebula.update;

import java.net.URI;

public record UpdateInfo(Version version, URI download, String sha256, long size, String changelog) {
}
