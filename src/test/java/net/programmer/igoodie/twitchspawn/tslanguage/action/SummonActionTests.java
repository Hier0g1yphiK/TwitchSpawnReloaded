package net.programmer.igoodie.twitchspawn.tslanguage.action;

import com.mojang.brigadier.StringReader;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.programmer.igoodie.twitchspawn.TestResources;
import net.programmer.igoodie.twitchspawn.tslanguage.event.EventArguments;
import net.programmer.igoodie.twitchspawn.tslanguage.parser.TSLSyntaxError;
import net.programmer.igoodie.twitchspawn.tslanguage.parser.TSLTokenizer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

public class SummonActionTests {

    @BeforeAll
    public static void bootstrap() {
        TestResources.bootstrapMinecraft();
    }

    @Test
    @DisplayName("should turn TSL coordinates into ones /summon accepts.")
    public void coordinateTest() throws Exception {
        Assertions.assertThrows(Exception.class, () -> Vec3Argument.vec3().parse(new StringReader("~ ~+10 ~")),
                "/summon is expected to reject a leading +");

        String[][] cases = {
                {"~", "~"}, {"~10", "~10"}, {"~+10", "~10"}, {"~-2.5", "~-2.5"},
                {"5", "5"}, {"+5", "5"}, {"-5", "-5"},
        };
        for (String[] c : cases) {
            String converted = SummonAction.validateCoordinateExpression(c[0]);
            Assertions.assertEquals(c[1], converted, c[0]);
            String position = converted + " " + converted + " " + converted;
            Assertions.assertDoesNotThrow(() -> Vec3Argument.vec3().parse(new StringReader(position)), position);
        }

        Assertions.assertThrows(TSLSyntaxError.class, () -> SummonAction.validateCoordinateExpression("~up"));
    }

    @Test
    @DisplayName("should fill in ${mobName} for the subtitle.")
    public void mobNameTest() throws TSLSyntaxError {
        List<String> words = TSLTokenizer.intoWords("minecraft:zombie ~ ~+10 ~");
        SummonAction action = new SummonAction(words);

        String mobName = action.subtitleEvaluator("mobName", EventArguments.createRandom("iGoodie"));
        Assertions.assertNotNull(mobName);
        Assertions.assertFalse(mobName.isBlank());
    }

}
