package ca.spottedleaf.moonrise.patches.serverlist;

public interface ServerListConnection {

    public int moonrise$getReadTimeout();

    public void moonrise$setReadTimeout(final int seconds);
}
