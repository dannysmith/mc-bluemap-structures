package dev.danny.bluemapstructures;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EndCityShipDetectorTest {

  private static final long SEED = 12345L;

  @Test
  void known_ship_positions() {
    // Known end cities WITH ships for seed 12345
    assertTrue(hasShip(-118, -120), "(-118,-120) should have ship");
    assertTrue(hasShip(-113, -95), "(-113,-95) should have ship");
    assertTrue(hasShip(-115, -15), "(-115,-15) should have ship");
    assertTrue(hasShip(-100, -17), "(-100,-17) should have ship");
    assertTrue(hasShip(-75, -114), "(-75,-114) should have ship");
    assertTrue(hasShip(5, -16), "(5,-16) should have ship");
    assertTrue(hasShip(24, -113), "(24,-113) should have ship");
    assertTrue(hasShip(64, -114), "(64,-114) should have ship");
  }

  @Test
  void known_no_ship_positions() {
    // Known end cities WITHOUT ships for seed 12345
    assertFalse(hasShip(-117, -35), "(-117,-35) should NOT have ship");
    assertFalse(hasShip(-118, 4), "(-118,4) should NOT have ship");
    assertFalse(hasShip(-95, -118), "(-95,-118) should NOT have ship");
    assertFalse(hasShip(-98, -94), "(-98,-94) should NOT have ship");
    assertFalse(hasShip(4, -117), "(4,-117) should NOT have ship");
    assertFalse(hasShip(3, 1), "(3,1) should NOT have ship");
  }

  private boolean hasShip(int chunkX, int chunkZ) {
    return EndCityShipDetector.hasShip(SEED, chunkX, chunkZ);
  }
}
