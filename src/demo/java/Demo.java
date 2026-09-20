import com.galitianu.jev4j.ApiException;
import com.galitianu.jev4j.Choice;
import com.galitianu.jev4j.Describe;
import com.galitianu.jev4j.ModelCard;
import com.galitianu.jev4j.Noul;
import com.galitianu.jev4j.Score;
import com.galitianu.jev4j.SystemOneResponse;
import com.galitianu.jev4j.TypeSafeClient;
import java.util.Map;

/** Run with `./gradlew demo`. Needs TYPESAFE_API_KEY in the environment. */
public class Demo {

    enum Tone {
        CALM,
        @Describe("irritated but still polite") FRUSTRATED,
        @Describe("hostile or threatening") ANGRY
    }

    record Ticket(String subject, String body) {}

    public static void main(String[] args) {
        try (TypeSafeClient client = TypeSafeClient.create()) {
            System.out.println("Available models: "
                + client.models().list().stream().map(ModelCard::name).toList());

            Ticket ticket = new Ticket(
                "Charged twice this month",
                "Hi, I see two charges of $49 on my card for August. I only have one account. Please fix this ASAP, I'm pretty frustrated.");

            var isBilling = Noul.of("Is this ticket about billing?");
            var tone = Choice.of("What is the customer's tone?", Tone.class);
            var urgency = Score.of("How urgent is this ticket?", "can wait", "this week", "today", "right now");
            var refundRisk = Score.of("How likely is the customer to demand a refund?", "unlikely", "possible", "likely");

            SystemOneResponse res = client.systemOne(ticket, isBilling, tone, urgency, refundRisk);

            System.out.printf("billing?     %.2f%n", res.get(isBilling).noul());
            System.out.printf("tone         %s (%.2f)%n", res.get().choice(), res.get(tone).probability(res.get(tone).choice()));
            System.out.printf("urgency      %.2f on a 0-3 scale: %s%n", res.get(urgency).score(), res.get(urgency).legend());
            System.out.printf("refund risk  %.2f (%.2f confidence)%n", res.get(refundRisk).score(), res.get(refundRisk).confidence());
            System.out.printf("tokens       %d in / %d out, model %s%n", res.usage().inputTokens(), res.usage().outputTokens(), res.model());

            // String-keyed variant
            SystemOneResponse byKey = client.systemOne(ticket, Map.of("spam", Noul.of("Is this spam?")));
            System.out.printf("spam?        %.2f%n", byKey.noul("spam").noul());
        } catch (ApiException e) {
            System.err.println("API error " + e.status() + " (request " + e.requestId().orElse("unknown") + "): " + e.body());
            System.exit(1);
        }
    }
}
