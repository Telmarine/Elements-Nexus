package com.tac5studios.elementsnexus.vanish;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

/** Added to ChunkMap by a mixin: gives access to its list of tracked entities. */
public interface ChunkMapAccess {
    Int2ObjectMap<?> nexus$entityMap();
}
