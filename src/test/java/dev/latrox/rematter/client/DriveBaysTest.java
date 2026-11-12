package dev.latrox.rematter.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.latrox.rematter.content.CellStatus;
import java.util.List;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DriveBaysTest {
    /** Openings of the generated housing in model pixels: left column top to bottom, then the right column. */
    private static final float[][] OPENINGS = {{9, 10}, {9, 6}, {9, 2}, {1, 10}, {1, 6}, {1, 2}};
    private static final int ALL_BAYS = 0b111111, SHADE = 0xFFCCCCCC, LIGHT = 0x00B000D0;
    private static final float EPSILON = 1e-5F;

    /** Sprite n covers u from n to n + 1 and v from 0 to 1, so a coordinate names its sprite. */
    private static SpriteUv sprites() {
        float[] bounds = new float[DriveBays.SPRITES.length * 4];
        for (int sprite = 0; sprite < DriveBays.SPRITES.length; sprite++) {
            bounds[sprite * 4] = sprite; bounds[sprite * 4 + 2] = sprite + 1; bounds[sprite * 4 + 3] = 1;
        }
        return new SpriteUv(bounds);
    }
    private static int packed(int... perBay) {
        int word = 0;
        for (int bay = 0; bay < perBay.length; bay++) word |= perBay[bay] << bay * 3;
        return word;
    }
    private static int statuses(CellStatus... perBay) {
        int word = 0;
        for (int bay = 0; bay < perBay.length; bay++) word |= perBay[bay].ordinal() << bay * 3;
        return word;
    }
    private static List<RecordedQuads.Quad> draw(int mask, int tiers, int statuses) {
        var recorded = new RecordedQuads();
        DriveBays.emit(new PoseStack().last(), recorded, mask, tiers, statuses, sprites(), SHADE, LIGHT);
        return recorded.quads();
    }
    private static void assertSpan(RecordedQuads.Quad quad, int axis, float from, float to) {
        assertEquals(from / 16, quad.min(axis), EPSILON);
        assertEquals(to / 16, quad.max(axis), EPSILON);
    }
    private static int sprite(RecordedQuads.Quad quad) {
        return (int) Math.floor(quad.vertices().stream().mapToDouble(RecordedQuads.Vertex::u).average().orElseThrow());
    }

    @Test void everySampledSpriteIsShippedAndTheUnreadableLightBlinks() {
        for (String sprite : DriveBays.SPRITES)
            assertNotNull(DriveBays.class.getResource("/assets/rematter/textures/block/" + sprite + ".png"), sprite);
        assertNotNull(DriveBays.class.getResource("/assets/rematter/textures/block/led_red.png.mcmeta"));
    }

    @Test void anEmptyDriveDrawsNothing() {
        assertTrue(draw(0, packed(1, 2, 3, 1, 2, 3), statuses(CellStatus.STORED, CellStatus.FULL)).isEmpty());
    }

    @Test void everyOccupiedBayShowsOneFaceplateThatFillsItsOpening() {
        var quads = draw(ALL_BAYS, packed(1, 1, 1, 1, 1, 1), 0);
        assertEquals(6, quads.size());
        for (int bay = 0; bay < 6; bay++) {
            var face = quads.get(bay);
            assertEquals(OPENINGS[bay][0], DriveBays.x(bay));
            assertEquals(OPENINGS[bay][1], DriveBays.y(bay));
            assertSpan(face, 0, OPENINGS[bay][0], OPENINGS[bay][0] + 6);
            assertSpan(face, 1, OPENINGS[bay][1], OPENINGS[bay][1] + 3);
            assertSpan(face, 2, .5F, .5F);
            assertEquals(new Vector3f(0, 0, -1), face.side(), "The faceplate looks out of the front");
            for (var vertex : face.vertices()) {
                assertEquals(SHADE, vertex.color());
                assertEquals(LIGHT, vertex.light());
            }
        }
    }

    @Test void onlyTheOccupiedBaysAreDrawn() {
        var quads = draw(0b100010, packed(1, 1, 1, 1, 1, 1), 0);
        assertEquals(2, quads.size());
        assertSpan(quads.get(0), 1, OPENINGS[1][1], OPENINGS[1][1] + 3);
        assertSpan(quads.get(1), 0, OPENINGS[5][0], OPENINGS[5][0] + 6);
    }

    @Test void theFaceplateTextureStartsAtTheViewersTopLeftAndCoversTwelveBySixTexels() {
        var face = draw(1, packed(1), 0).getFirst();
        for (var vertex : face.vertices()) {
            // +X is the viewer's left, so the texture runs against the X axis.
            boolean left = Math.abs(vertex.x() - face.max(0)) < EPSILON, top = Math.abs(vertex.y() - face.max(1)) < EPSILON;
            assertEquals(left ? 0 : 12 / 16F, vertex.u(), EPSILON);
            assertEquals(top ? 0 : 6 / 16F, vertex.v(), EPSILON);
        }
    }

    @Test void theTierChoosesTheFaceplateAndAnythingElseIsAnUnreadableCell() {
        var quads = draw(ALL_BAYS, packed(1, 2, 3, 4, 0, 7), 0);
        int[] expected = {0, 1, 2, 3, 3, 10};
        for (int bay = 0; bay < 6; bay++) assertEquals(expected[bay], sprite(quads.get(bay)), "bay " + bay);
        assertEquals("drive_cell_64k", DriveBays.SPRITES[0]);
        assertEquals("drive_cell_256k", DriveBays.SPRITES[1]);
        assertEquals("drive_cell_1m", DriveBays.SPRITES[2]);
        assertEquals("drive_cell_unknown", DriveBays.SPRITES[3]);
    }

    @Test void primalReserveHasDistinctFaceplatesAndKeepsTheSameStatusSemantics(){
        assertEquals("drive_primal_cell_16k",DriveBays.SPRITES[DriveBays.cellSprite(5)]);
        assertEquals("drive_primal_cell_64k",DriveBays.SPRITES[DriveBays.cellSprite(6)]);
        assertEquals("drive_primal_cell_256k",DriveBays.SPRITES[DriveBays.cellSprite(7)]);
        assertEquals(3,DriveBays.cellSprite(4));
    }
    @Test void theStatusChoosesTheLightAndAMissingStatusShowsNone() {
        assertEquals("led_blue", DriveBays.SPRITES[DriveBays.ledSprite(CellStatus.EMPTY)]);
        assertEquals("led_green", DriveBays.SPRITES[DriveBays.ledSprite(CellStatus.STORED)]);
        assertEquals("led_amber", DriveBays.SPRITES[DriveBays.ledSprite(CellStatus.FULL)]);
        assertEquals("led_red", DriveBays.SPRITES[DriveBays.ledSprite(CellStatus.LOCKED)]);
        assertEquals(-1, DriveBays.ledSprite(CellStatus.ABSENT));
        var quads = draw(0b11, packed(1, 1), statuses(CellStatus.ABSENT, CellStatus.FULL));
        assertEquals(1 + 6, quads.size(), "Only the second bay has a light");
        for (var quad : quads.subList(2, 7)) assertEquals(DriveBays.ledSprite(CellStatus.FULL), sprite(quad));
    }

    @Test void theStatusLightIsAFullBrightPixelStandingOnTheFaceplate() {
        for (int bay = 0; bay < 6; bay++) {
            var quads = draw(1 << bay, packed(1, 1, 1, 1, 1, 1),
                    statuses(CellStatus.EMPTY, CellStatus.EMPTY, CellStatus.EMPTY, CellStatus.EMPTY, CellStatus.EMPTY, CellStatus.EMPTY));
            assertEquals(6, quads.size());
            float x0 = OPENINGS[bay][0] + 4.5F, y0 = OPENINGS[bay][1] + 1;
            var seen = new java.util.HashSet<Vector3f>();
            for (var quad : quads.subList(1, 6)) {
                assertTrue(quad.planar());
                for (var vertex : quad.vertices()) {
                    assertEquals(0xFFFFFFFF, vertex.color());
                    assertEquals(LightCoordsUtil.FULL_BRIGHT, vertex.light());
                    assertTrue(vertex.x() >= x0 / 16 - EPSILON && vertex.x() <= (x0 + 1) / 16 + EPSILON);
                    assertTrue(vertex.y() >= y0 / 16 - EPSILON && vertex.y() <= (y0 + 1) / 16 + EPSILON);
                    assertTrue(vertex.z() >= -EPSILON && vertex.z() <= .5F / 16 + EPSILON);
                }
                var facing = quad.side();
                assertTrue(seen.add(facing), "Each side is drawn once");
                // A side lies on the face of the pixel that it looks out of.
                for (int axis = 0; axis < 3; axis++) {
                    if (facing.get(axis) == 0) continue;
                    float[] low = {x0, y0, 0}, high = {x0 + 1, y0 + 1, .5F};
                    assertSpan(quad, axis, facing.get(axis) > 0 ? high[axis] : low[axis], facing.get(axis) > 0 ? high[axis] : low[axis]);
                }
            }
            assertEquals(java.util.Set.of(new Vector3f(0, 0, -1), new Vector3f(1, 0, 0), new Vector3f(-1, 0, 0),
                    new Vector3f(0, 1, 0), new Vector3f(0, -1, 0)), seen, "Front, both sides, top and bottom; the back is covered");
        }
    }

    @Test void quadsFollowTheRotationOfTheBlock() {
        var stack = new PoseStack();
        stack.translate(3, -2, 5);
        stack.mulPose(Axis.YP.rotationDegrees(90));
        var recorded = new RecordedQuads();
        DriveBays.emit(stack.last(), recorded, 1, packed(1), 0, sprites(), SHADE, LIGHT);
        var face = recorded.quads().getFirst();
        var expected = stack.last().pose().transformPosition(new Vector3f(15 / 16F, 13 / 16F, .5F / 16));
        var first = face.vertices().getFirst();
        assertEquals(expected.x, first.x(), EPSILON);
        assertEquals(expected.y, first.y(), EPSILON);
        assertEquals(expected.z, first.z(), EPSILON);
        // A quarter turn about the vertical axis carries the model's north to the west.
        assertEquals(-1, face.facing().x, EPSILON);
    }
}
