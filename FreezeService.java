package com.staffops.staff;
import java.util.Set; import java.util.UUID; import java.util.concurrent.ConcurrentHashMap;
public final class FreezeService { private final Set<UUID> frozen=ConcurrentHashMap.newKeySet(); public boolean toggle(UUID id){if(frozen.remove(id))return false;frozen.add(id);return true;} public boolean isFrozen(UUID id){return frozen.contains(id);} public boolean set(UUID id,boolean state){if(state)frozen.add(id);else frozen.remove(id);return state;} public void clear(){frozen.clear();} }
