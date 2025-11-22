package com.example.mechanicassistant.utils;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class StructureSerializer {
    
    public static String serializeStructure(Location loc1, Location loc2) {
        try {
            World world = loc1.getWorld();
            int minX = Math.min(loc1.getBlockX(), loc2.getBlockX());
            int minY = Math.min(loc1.getBlockY(), loc2.getBlockY());
            int minZ = Math.min(loc1.getBlockZ(), loc2.getBlockZ());
            int maxX = Math.max(loc1.getBlockX(), loc2.getBlockX());
            int maxY = Math.max(loc1.getBlockY(), loc2.getBlockY());
            int maxZ = Math.max(loc1.getBlockZ(), loc2.getBlockZ());
            
            JSONObject structureJson = new JSONObject();
            structureJson.put("version", "1.0");
            structureJson.put("size_x", maxX - minX + 1);
            structureJson.put("size_y", maxY - minY + 1);
            structureJson.put("size_z", maxZ - minZ + 1);
            structureJson.put("world", world.getName());
            
            JSONArray blocksArray = new JSONArray();
            int blockCount = 0;
            
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        Block block = world.getBlockAt(x, y, z);
                        if (block.getType() == Material.AIR) continue;
                        
                        JSONObject blockData = new JSONObject();
                        blockData.put("x", x - minX);
                        blockData.put("y", y - minY);
                        blockData.put("z", z - minZ);
                        blockData.put("type", block.getType().name());
                        
                        // 保存方块数据
                        BlockData bd = block.getBlockData();
                        blockData.put("block_data", bd.getAsString());
                        
                        blocksArray.put(blockData);
                        blockCount++;
                    }
                }
            }
            
            structureJson.put("blocks", blocksArray);
            structureJson.put("block_count", blockCount);
            
            return structureJson.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public static boolean pasteStructure(Location pasteLoc, String structureData, BlockFace facing) {
        try {
            JSONObject structureJson = new JSONObject(structureData);
            int sizeX = structureJson.getInt("size_x");
            int sizeY = structureJson.getInt("size_y");
            int sizeZ = structureJson.getInt("size_z");
            JSONArray blocksArray = structureJson.getJSONArray("blocks");
            
            World world = pasteLoc.getWorld();
            int baseX = pasteLoc.getBlockX();
            int baseY = pasteLoc.getBlockY();
            int baseZ = pasteLoc.getBlockZ();
            
            // 检查放置空间
            if (!canPlaceStructure(world, baseX, baseY, baseZ, sizeX, sizeY, sizeZ, facing)) {
                return false;
            }
            
            // 放置方块
            for (int i = 0; i < blocksArray.length(); i++) {
                JSONObject blockData = blocksArray.getJSONObject(i);
                int relX = blockData.getInt("x");
                int relY = blockData.getInt("y");
                int relZ = blockData.getInt("z");
                
                // 计算旋转后的坐标
                int[] rotatedCoords = applyRotation(relX, relZ, facing, sizeX, sizeZ);
                
                int worldX = baseX + rotatedCoords[0];
                int worldY = baseY + relY;
                int worldZ = baseZ + rotatedCoords[1];
                
                Material blockType = Material.valueOf(blockData.getString("type"));
                Block block = world.getBlockAt(worldX, worldY, worldZ);
                
                // 设置方块类型和数据
                block.setType(blockType, false);
                
                if (blockData.has("block_data")) {
                    String blockDataStr = blockData.getString("block_data");
                    try {
                        BlockData bd = Bukkit.createBlockData(blockDataStr);
                        // 应用旋转到方块数据
                        bd = rotateBlockData(bd, facing);
                        block.setBlockData(bd, false);
                    } catch (IllegalArgumentException e) {
                        // 忽略无法解析的方块数据
                    }
                }
            }
            
            // 强制更新方块
            world.getChunkAt(pasteLoc).load();
            
            return true;
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public static void clearStructure(Location loc1, Location loc2) {
        World world = loc1.getWorld();
        int minX = Math.min(loc1.getBlockX(), loc2.getBlockX());
        int minY = Math.min(loc1.getBlockY(), loc2.getBlockY());
        int minZ = Math.min(loc1.getBlockZ(), loc2.getBlockZ());
        int maxX = Math.max(loc1.getBlockX(), loc2.getBlockX());
        int maxY = Math.max(loc1.getBlockY(), loc2.getBlockY());
        int maxZ = Math.max(loc1.getBlockZ(), loc2.getBlockZ());
        
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }
    
    private static boolean canPlaceStructure(World world, int baseX, int baseY, int baseZ, 
                                           int sizeX, int sizeY, int sizeZ, BlockFace facing) {
        // 检查结构是否会超出世界边界
        if (baseY + sizeY > world.getMaxHeight() || baseY < world.getMinHeight()) {
            return false;
        }
        
        // 检查放置区域是否被占用（只检查非空气方块）
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    int[] rotatedCoords = applyRotation(x, z, facing, sizeX, sizeZ);
                    Block block = world.getBlockAt(baseX + rotatedCoords[0], baseY + y, baseZ + rotatedCoords[1]);
                    if (!block.getType().isAir() && block.getType().isSolid()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
    
    private static int[] applyRotation(int x, int z, BlockFace facing, int sizeX, int sizeZ) {
        return switch (facing) {
            case SOUTH -> new int[]{sizeX - 1 - x, sizeZ - 1 - z};
            case WEST -> new int[]{z, sizeX - 1 - x};
            case EAST -> new int[]{sizeZ - 1 - z, x};
            default -> new int[]{x, z}; // NORTH
        };
    }
    
    private static BlockData rotateBlockData(BlockData bd, BlockFace facing) {
        // 简化版的方块数据旋转逻辑
        // 实际应用中可能需要更复杂的处理来旋转不同种类的方块
        String dataString = bd.getAsString();
        
        // 处理朝向性方块的旋转
        if (dataString.contains("facing=")) {
            String currentFacing = dataString.split("facing=")[1].split("[,\\]]")[0];
            String newFacing = rotateFacingString(currentFacing, facing);
            dataString = dataString.replace("facing=" + currentFacing, "facing=" + newFacing);
        }
        
        try {
            return Bukkit.createBlockData(dataString);
        } catch (IllegalArgumentException e) {
            return bd; // 如果旋转失败，返回原始数据
        }
    }
    
    private static String rotateFacingString(String currentFacing, BlockFace rotation) {
        // 简化版的朝向旋转逻辑
        return switch (currentFacing) {
            case "north" -> rotation == BlockFace.EAST ? "east" : rotation == BlockFace.WEST ? "west" : rotation == BlockFace.SOUTH ? "south" : "north";
            case "east" -> rotation == BlockFace.EAST ? "south" : rotation == BlockFace.WEST ? "north" : rotation == BlockFace.SOUTH ? "west" : "east";
            case "south" -> rotation == BlockFace.EAST ? "west" : rotation == BlockFace.WEST ? "east" : rotation == BlockFace.SOUTH ? "north" : "south";
            case "west" -> rotation == BlockFace.EAST ? "north" : rotation == BlockFace.WEST ? "south" : rotation == BlockFace.SOUTH ? "east" : "west";
            default -> currentFacing;
        };
    }
}
