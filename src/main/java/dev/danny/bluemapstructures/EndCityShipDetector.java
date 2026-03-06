package dev.danny.bluemapstructures;

import java.util.Random;

/**
 * Determines whether an End City at a given chunk position has a ship. Ports the recursive
 * piece-generation algorithm from vanilla MC / cubiomes.
 *
 * <p>Reference implementations:
 *
 * <ul>
 *   <li>cubiomes finders.c — getEndCityPieces()
 *   <li>KaptainWutax FeatureUtils — EndCityGenerator.java
 * </ul>
 */
public class EndCityShipDetector {

  private static final int MAX_PIECES = 421;

  // Piece type indices
  private static final int BASE_FLOOR = 0;
  private static final int BASE_ROOF = 1;
  private static final int BRIDGE_END = 2;
  private static final int BRIDGE_GENTLE_STAIRS = 3;
  private static final int BRIDGE_PIECE = 4;
  private static final int BRIDGE_STEEP_STAIRS = 5;
  private static final int FAT_TOWER_BASE = 6;
  private static final int FAT_TOWER_MIDDLE = 7;
  private static final int FAT_TOWER_TOP = 8;
  private static final int SECOND_FLOOR_1 = 9;
  private static final int SECOND_FLOOR_2 = 10;
  private static final int SECOND_ROOF = 11;
  private static final int SHIP = 12;
  private static final int THIRD_FLOOR_1 = 13;
  private static final int THIRD_FLOOR_2 = 14;
  private static final int THIRD_ROOF = 15;
  private static final int TOWER_BASE = 16;
  private static final int TOWER_PIECE = 17;
  private static final int TOWER_TOP = 18;

  // Piece sizes (width-1, height-1, depth-1) — cubiomes uses size-1 for inclusive bounding boxes
  private static final int[][] PIECE_SIZES = {
    {9, 3, 9}, // base_floor
    {11, 1, 11}, // base_roof
    {4, 5, 1}, // bridge_end
    {4, 6, 7}, // bridge_gentle_stairs
    {4, 5, 3}, // bridge_piece
    {4, 6, 3}, // bridge_steep_stairs
    {12, 3, 12}, // fat_tower_base
    {12, 7, 12}, // fat_tower_middle
    {16, 5, 16}, // fat_tower_top
    {11, 7, 11}, // second_floor_1
    {11, 7, 11}, // second_floor_2
    {13, 1, 13}, // second_roof
    {12, 23, 28}, // ship
    {13, 7, 13}, // third_floor_1
    {13, 7, 13}, // third_floor_2
    {15, 1, 15}, // third_roof
    {6, 6, 6}, // tower_base
    {6, 3, 6}, // tower_piece
    {8, 4, 8}, // tower_top
  };

  // Bridge direction offsets for towers: {rotationDelta, x, y, z}
  private static final int[][] TOWER_BRIDGES = {
    {0, 1, -1, 0}, {1, 6, -1, 1}, {3, 0, -1, 5}, {2, 5, -1, 6},
  };

  // Bridge direction offsets for fat towers
  private static final int[][] FAT_TOWER_BRIDGES = {
    {0, 4, -1, 0}, {1, 12, -1, 4}, {3, 0, -1, 8}, {2, 8, -1, 12},
  };

  // Each piece is stored as 11 ints:
  // [0-5] = minX, minY, minZ, maxX, maxY, maxZ (bounding box)
  // [6] = genDepth (collision group tag)
  // [7] = rotation
  // [8-10] = posX, posY, posZ (origin position for child offset computation)
  private static final int STRIDE = 11;

  public static boolean hasShip(long worldSeed, int chunkX, int chunkZ) {
    Random rand = new Random(worldSeed);
    long a = rand.nextLong();
    long b = rand.nextLong();
    rand = new Random((long) chunkX * a ^ (long) chunkZ * b ^ worldSeed);

    int rot = rand.nextInt(4);
    int[] pieces = new int[MAX_PIECES * STRIDE];
    int n = 0;
    boolean[] shipFound = {false};

    int startX = chunkX * 16 + 8;
    int startZ = chunkZ * 16 + 8;
    n = addPiece(pieces, n, -1, rot, startX, 0, startZ, BASE_FLOOR);
    n = addPiece(pieces, n, n - 1, rot, -1, 0, -1, SECOND_FLOOR_1);
    n = addPiece(pieces, n, n - 1, rot, -1, 4, -1, THIRD_FLOOR_1);
    n = addPiece(pieces, n, n - 1, rot, -1, 8, -1, THIRD_ROOF);

    int[] nRef = {n};
    genRecursive(0, pieces, nRef, n - 1, 1, rand, shipFound);
    return shipFound[0];
  }

  // Generator IDs for dispatch
  private static final int GEN_TOWER = 0;
  private static final int GEN_BRIDGE = 1;
  private static final int GEN_HOUSE_TOWER = 2;
  private static final int GEN_FAT_TOWER = 3;

  private static boolean genRecursive(
      int genId,
      int[] pieces,
      int[] nRef,
      int currentIdx,
      int depth,
      Random rand,
      boolean[] shipFound) {
    return genRecursive(genId, pieces, nRef, currentIdx, depth, rand, shipFound, 0);
  }

  private static boolean genRecursive(
      int genId,
      int[] pieces,
      int[] nRef,
      int currentIdx,
      int depth,
      Random rand,
      boolean[] shipFound,
      int extraY) {
    if (depth > 8) return false;

    int nBefore = nRef[0];

    boolean ok;
    switch (genId) {
      case GEN_TOWER:
        ok = genTower(pieces, nRef, currentIdx, depth, rand, shipFound);
        break;
      case GEN_BRIDGE:
        ok = genBridge(pieces, nRef, currentIdx, depth, rand, shipFound);
        break;
      case GEN_HOUSE_TOWER:
        ok = genHouseTower(pieces, nRef, currentIdx, depth, rand, shipFound, extraY);
        break;
      case GEN_FAT_TOWER:
        ok = genFatTower(pieces, nRef, currentIdx, depth, rand, shipFound);
        break;
      default:
        return false;
    }

    if (!ok) {
      nRef[0] = nBefore;
      return false;
    }

    int genDepth = rand.nextInt();
    int nAfter = nRef[0];

    for (int i = nBefore; i < nAfter; i++) {
      int pi = i * STRIDE;
      pieces[pi + 6] = genDepth;

      for (int j = 0; j < nBefore; j++) {
        int pj = j * STRIDE;
        if (intersects(pieces, pi, pj)) {
          int currentGenDepth = pieces[currentIdx * STRIDE + 6];
          if (currentGenDepth != pieces[pj + 6]) {
            nRef[0] = nBefore;
            return false;
          }
          break;
        }
      }
    }

    return true;
  }

  private static boolean genTower(
      int[] pieces, int[] nRef, int currentIdx, int depth, Random rand, boolean[] shipFound) {
    int rot = pieces[currentIdx * STRIDE + 7];
    int x = 3 + rand.nextInt(2);
    int z = 3 + rand.nextInt(2);

    nRef[0] = addPiece(pieces, nRef[0], currentIdx, rot, x, -3, z, TOWER_BASE);
    int baseIdx = nRef[0] - 1;
    nRef[0] = addPiece(pieces, nRef[0], baseIdx, rot, 0, 7, 0, TOWER_PIECE);
    baseIdx = nRef[0] - 1;

    int floorIdx = (rand.nextInt(3) == 0) ? baseIdx : -1;
    int floorCnt = 1 + rand.nextInt(3);

    for (int i = 0; i < floorCnt; i++) {
      nRef[0] = addPiece(pieces, nRef[0], baseIdx, rot, 0, 4, 0, TOWER_PIECE);
      baseIdx = nRef[0] - 1;
      if (i < floorCnt - 1 && rand.nextBoolean()) {
        floorIdx = baseIdx;
      }
    }

    if (floorIdx >= 0) {
      for (int[] bridge : TOWER_BRIDGES) {
        if (rand.nextBoolean()) {
          int brot = (rot + bridge[0]) & 3;
          nRef[0] =
              addPiece(pieces, nRef[0], baseIdx, brot, bridge[1], bridge[2], bridge[3], BRIDGE_END);
          int bridgeIdx = nRef[0] - 1;
          genRecursive(GEN_BRIDGE, pieces, nRef, bridgeIdx, depth + 1, rand, shipFound);
        }
      }
    } else if (depth != 7) {
      return genRecursive(GEN_FAT_TOWER, pieces, nRef, baseIdx, depth + 1, rand, shipFound);
    }

    nRef[0] = addPiece(pieces, nRef[0], baseIdx, rot, -1, 4, -1, TOWER_TOP);
    return true;
  }

  private static boolean genBridge(
      int[] pieces, int[] nRef, int currentIdx, int depth, Random rand, boolean[] shipFound) {
    int rot = pieces[currentIdx * STRIDE + 7];
    int floorCnt = 1 + rand.nextInt(4);

    nRef[0] = addPiece(pieces, nRef[0], currentIdx, rot, 0, 0, -4, BRIDGE_PIECE);
    int baseIdx = nRef[0] - 1;
    pieces[baseIdx * STRIDE + 6] = -1; // immune to collision

    int y = 0;
    for (int i = 0; i < floorCnt; i++) {
      if (rand.nextBoolean()) {
        nRef[0] = addPiece(pieces, nRef[0], baseIdx, rot, 0, y, -4, BRIDGE_PIECE);
        baseIdx = nRef[0] - 1;
        y = 0;
      } else {
        if (rand.nextBoolean()) {
          nRef[0] = addPiece(pieces, nRef[0], baseIdx, rot, 0, y, -4, BRIDGE_STEEP_STAIRS);
        } else {
          nRef[0] = addPiece(pieces, nRef[0], baseIdx, rot, 0, y, -8, BRIDGE_GENTLE_STAIRS);
        }
        baseIdx = nRef[0] - 1;
        y = 4;
      }
    }

    if (!shipFound[0] && rand.nextInt(10 - depth) == 0) {
      int sx = -8 + rand.nextInt(8);
      int sz = -70 + rand.nextInt(10);
      nRef[0] = addPiece(pieces, nRef[0], baseIdx, rot, sx, y, sz, SHIP);
      shipFound[0] = true;
    } else {
      if (!genRecursive(
          GEN_HOUSE_TOWER, pieces, nRef, baseIdx, depth + 1, rand, shipFound, y + 1)) {
        return false;
      }
    }

    nRef[0] = addPiece(pieces, nRef[0], baseIdx, (rot + 2) & 3, 4, y, 0, BRIDGE_END);
    int endIdx = nRef[0] - 1;
    pieces[endIdx * STRIDE + 6] = -1;
    return true;
  }

  private static boolean genHouseTower(
      int[] pieces,
      int[] nRef,
      int currentIdx,
      int depth,
      Random rand,
      boolean[] shipFound,
      int houseY) {
    if (depth > 8) return false;
    int rot = pieces[currentIdx * STRIDE + 7];

    nRef[0] = addPiece(pieces, nRef[0], currentIdx, rot, -3, houseY, -11, BASE_FLOOR);
    int baseIdx = nRef[0] - 1;

    int size = rand.nextInt(3);
    if (size == 0) {
      nRef[0] = addPiece(pieces, nRef[0], baseIdx, rot, -1, 4, -1, BASE_ROOF);
      return true;
    }
    nRef[0] = addPiece(pieces, nRef[0], baseIdx, rot, -1, 0, -1, SECOND_FLOOR_2);
    baseIdx = nRef[0] - 1;
    if (size == 1) {
      nRef[0] = addPiece(pieces, nRef[0], baseIdx, rot, -1, 8, -1, SECOND_ROOF);
    } else {
      nRef[0] = addPiece(pieces, nRef[0], baseIdx, rot, -1, 4, -1, THIRD_FLOOR_2);
      baseIdx = nRef[0] - 1;
      nRef[0] = addPiece(pieces, nRef[0], baseIdx, rot, -1, 8, -1, THIRD_ROOF);
    }
    baseIdx = nRef[0] - 1;
    genRecursive(GEN_TOWER, pieces, nRef, baseIdx, depth + 1, rand, shipFound);
    return true;
  }

  private static boolean genFatTower(
      int[] pieces, int[] nRef, int currentIdx, int depth, Random rand, boolean[] shipFound) {
    int rot = pieces[currentIdx * STRIDE + 7];

    nRef[0] = addPiece(pieces, nRef[0], currentIdx, rot, -3, 4, -3, FAT_TOWER_BASE);
    int baseIdx = nRef[0] - 1;
    nRef[0] = addPiece(pieces, nRef[0], baseIdx, rot, 0, 4, 0, FAT_TOWER_MIDDLE);
    baseIdx = nRef[0] - 1;

    for (int j = 0; j < 2 && rand.nextInt(3) != 0; j++) {
      nRef[0] = addPiece(pieces, nRef[0], baseIdx, rot, 0, 8, 0, FAT_TOWER_MIDDLE);
      baseIdx = nRef[0] - 1;
      for (int[] bridge : FAT_TOWER_BRIDGES) {
        if (rand.nextBoolean()) {
          int brot = (rot + bridge[0]) & 3;
          nRef[0] =
              addPiece(pieces, nRef[0], baseIdx, brot, bridge[1], bridge[2], bridge[3], BRIDGE_END);
          int bridgeIdx = nRef[0] - 1;
          genRecursive(GEN_BRIDGE, pieces, nRef, bridgeIdx, depth + 1, rand, shipFound);
        }
      }
    }

    nRef[0] = addPiece(pieces, nRef[0], baseIdx, rot, -2, 8, -2, FAT_TOWER_TOP);
    return true;
  }

  private static boolean intersects(int[] pieces, int pi, int pj) {
    return pieces[pi + 3] >= pieces[pj]
        && pieces[pi] <= pieces[pj + 3]
        && pieces[pi + 4] >= pieces[pj + 1]
        && pieces[pi + 1] <= pieces[pj + 4]
        && pieces[pi + 5] >= pieces[pj + 2]
        && pieces[pi + 2] <= pieces[pj + 5];
  }

  /**
   * Adds a piece to the flat array, computing its bounding box. Returns the new piece count.
   *
   * @param prevIdx index of parent piece (-1 for no parent / absolute position)
   */
  private static int addPiece(
      int[] pieces, int n, int prevIdx, int rot, int px, int py, int pz, int type) {
    int[] sz = PIECE_SIZES[type];
    int sx = sz[0], sy = sz[1], szz = sz[2];

    int posX, posY, posZ;
    if (prevIdx < 0) {
      posX = px;
      posY = py;
      posZ = pz;
    } else {
      int prevOff = prevIdx * STRIDE;
      int prevRot = pieces[prevOff + 7];
      int dx, dz;
      switch (prevRot) {
        case 0:
          dx = px;
          dz = pz;
          break;
        case 1:
          dx = -pz;
          dz = px;
          break;
        case 2:
          dx = -px;
          dz = -pz;
          break;
        case 3:
          dx = pz;
          dz = -px;
          break;
        default:
          throw new IllegalStateException();
      }
      posX = pieces[prevOff + 8] + dx;
      posY = pieces[prevOff + 9] + py;
      posZ = pieces[prevOff + 10] + dz;
    }

    int minX, minZ, maxX, maxZ;
    switch (rot) {
      case 0:
        minX = posX;
        minZ = posZ;
        maxX = posX + sx;
        maxZ = posZ + szz;
        break;
      case 1:
        minX = posX - szz;
        minZ = posZ;
        maxX = posX;
        maxZ = posZ + sx;
        break;
      case 2:
        minX = posX - sx;
        minZ = posZ - szz;
        maxX = posX;
        maxZ = posZ;
        break;
      case 3:
        minX = posX;
        minZ = posZ - sx;
        maxX = posX + szz;
        maxZ = posZ;
        break;
      default:
        throw new IllegalStateException();
    }

    int off = n * STRIDE;
    pieces[off] = minX;
    pieces[off + 1] = posY;
    pieces[off + 2] = minZ;
    pieces[off + 3] = maxX;
    pieces[off + 4] = posY + sy;
    pieces[off + 5] = maxZ;
    pieces[off + 6] = 0; // genDepth
    pieces[off + 7] = rot;
    pieces[off + 8] = posX;
    pieces[off + 9] = posY;
    pieces[off + 10] = posZ;

    return n + 1;
  }
}
