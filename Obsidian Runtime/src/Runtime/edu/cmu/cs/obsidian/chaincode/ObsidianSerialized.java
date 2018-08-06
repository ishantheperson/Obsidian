package edu.cmu.cs.obsidian.chaincode;
import java.util.Set;

public interface ObsidianSerialized {
    String __getGUID();
    Set<ObsidianSerialized> __getModified(Set<ObsidianSerialized> checked);
    byte[] __archiveBytes();
}
