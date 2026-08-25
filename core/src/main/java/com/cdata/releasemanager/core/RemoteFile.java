package com.cdata.releasemanager.core;

/** An object in a bucket listing: transport vocabulary, produced by {@code BucketReader}. */
public record RemoteFile(String key, long size) {

    public String filename() {
        return key.substring(key.lastIndexOf('/') + 1);
    }
}
