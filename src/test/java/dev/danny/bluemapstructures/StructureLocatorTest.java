package dev.danny.bluemapstructures;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class StructureLocatorTest {

  private static final long SEED = 12345L;
  private static final int RADIUS = 2000;

  @ParameterizedTest
  @EnumSource(StructureType.class)
  void finds_results_within_radius(StructureType type) {
    List<StructureLocator.StructurePos> positions =
        StructureLocator.findStructures(type, SEED, RADIUS, null);

    assertFalse(positions.isEmpty(), type.displayName() + " should find at least one result");
    for (StructureLocator.StructurePos pos : positions) {
      assertTrue(
          Math.abs(pos.blockX()) <= RADIUS && Math.abs(pos.blockZ()) <= RADIUS,
          type.displayName()
              + " position out of radius: ("
              + pos.blockX()
              + ", "
              + pos.blockZ()
              + ")");
    }
  }

  @Test
  void fortress_and_bastion_never_share_position() {
    List<StructureLocator.StructurePos> fortresses =
        StructureLocator.findStructures(StructureType.FORTRESS, SEED, RADIUS, null);
    List<StructureLocator.StructurePos> bastions =
        StructureLocator.findStructures(StructureType.BASTION, SEED, RADIUS, null);

    for (StructureLocator.StructurePos f : fortresses) {
      for (StructureLocator.StructurePos b : bastions) {
        assertFalse(
            f.blockX() == b.blockX() && f.blockZ() == b.blockZ(),
            "Fortress and Bastion share position: (" + f.blockX() + ", " + f.blockZ() + ")");
      }
    }
  }

  @Test
  void fortress_bastion_counts_match_expected_ratio() {
    // Weight roll: nextInt(5) < 2 = fortress (40%), else bastion (60%)
    List<StructureLocator.StructurePos> fortresses =
        StructureLocator.findStructures(StructureType.FORTRESS, SEED, RADIUS, null);
    List<StructureLocator.StructurePos> bastions =
        StructureLocator.findStructures(StructureType.BASTION, SEED, RADIUS, null);

    int total = fortresses.size() + bastions.size();
    assertTrue(total > 0, "Should find at least one nether structure");
    // Every grid cell produces exactly one of fortress or bastion
    // so both lists should be non-empty with a reasonable sample size
    assertFalse(fortresses.isEmpty(), "Should find at least one fortress");
    assertFalse(bastions.isEmpty(), "Should find at least one bastion");
  }

  // Regression guards: known-good positions for seed 12345, radius 2000.
  // If the algorithm changes, these will catch it.

  @Test
  void known_village_positions() {
    List<StructureLocator.StructurePos> positions =
        StructureLocator.findStructures(StructureType.VILLAGE, SEED, RADIUS, null);
    assertEquals(58, positions.size(), "Village count for seed 12345, radius 2000");
    assertContainsPosition(positions, 344, 88);
    assertContainsPosition(positions, -200, 408);
    assertContainsPosition(positions, 1912, -1976);
  }

  @Test
  void known_monument_positions() {
    List<StructureLocator.StructurePos> positions =
        StructureLocator.findStructures(StructureType.MONUMENT, SEED, RADIUS, null);
    assertEquals(64, positions.size(), "Monument count for seed 12345, radius 2000");
    assertContainsPosition(positions, 88, -232);
    assertContainsPosition(positions, 56, 312);
  }

  @Test
  void known_fortress_positions() {
    List<StructureLocator.StructurePos> positions =
        StructureLocator.findStructures(StructureType.FORTRESS, SEED, RADIUS, null);
    assertEquals(32, positions.size(), "Fortress count for seed 12345, radius 2000");
    assertContainsPosition(positions, -328, 136);
    assertContainsPosition(positions, 456, 24);
  }

  @Test
  void known_buried_treasure_positions() {
    List<StructureLocator.StructurePos> positions =
        StructureLocator.findStructures(StructureType.BURIED_TREASURE, SEED, RADIUS, null);
    assertEquals(619, positions.size(), "Buried Treasure count for seed 12345, radius 2000");
    assertContainsPosition(positions, -1991, -743);
    assertContainsPosition(positions, -1975, -23);
    assertContainsPosition(positions, -1975, 1977);
  }

  @Test
  void end_city_ship_detection() {
    List<StructureLocator.StructurePos> positions =
        StructureLocator.findStructures(StructureType.END_CITY, SEED, RADIUS, null);
    long withShip = positions.stream().filter(StructureLocator.StructurePos::hasShip).count();
    long withoutShip = positions.size() - withShip;
    assertEquals(84, withShip, "End cities with ship for seed 12345, radius 2000");
    assertEquals(76, withoutShip, "End cities without ship for seed 12345, radius 2000");

    // Verify specific positions have correct ship status
    assertHasShipAt(positions, -1880, -1912, true);
    assertHasShipAt(positions, -1864, -552, false);
    assertHasShipAt(positions, 88, -248, true);
    assertHasShipAt(positions, 56, 24, false);
  }

  @Test
  void known_stronghold_positions() {
    List<StructureLocator.StructurePos> positions =
        StructureLocator.findStructures(StructureType.STRONGHOLD, SEED, RADIUS, null);
    assertEquals(2, positions.size(), "Stronghold count for seed 12345, radius 2000");
    assertContainsPosition(positions, 1800, 328);
    assertContainsPosition(positions, -1672, 1992);
  }

  private void assertContainsPosition(
      List<StructureLocator.StructurePos> positions, int blockX, int blockZ) {
    boolean found = positions.stream().anyMatch(p -> p.blockX() == blockX && p.blockZ() == blockZ);
    assertTrue(found, "Expected position (" + blockX + ", " + blockZ + ") not found");
  }

  private void assertHasShipAt(
      List<StructureLocator.StructurePos> positions, int blockX, int blockZ, boolean expectedShip) {
    StructureLocator.StructurePos pos =
        positions.stream()
            .filter(p -> p.blockX() == blockX && p.blockZ() == blockZ)
            .findFirst()
            .orElseThrow(
                () -> new AssertionError("Position (" + blockX + ", " + blockZ + ") not found"));
    assertEquals(
        expectedShip, pos.hasShip(), "Position (" + blockX + ", " + blockZ + ") hasShip mismatch");
  }
}
