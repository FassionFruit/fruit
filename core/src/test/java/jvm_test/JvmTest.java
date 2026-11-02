import io.github.fassionfruit.FFM;
import io.github.fassionfruit.annotation.NativeLibrary;
import io.github.fassionfruit.annotation.NativeSymbol;
import io.github.fassionfruit.annotation.structure.JvmMethod;
import io.github.fassionfruit.annotation.structure.JvmStructure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@JvmStructure
public interface Player {

    @JvmMethod(order = 0)
    void showDeathAnimation();

    @JvmMethod(order = 1)
    int getHealth();

    @JvmMethod(order = 2)
    int addHealth(int amount);
}

public class PlayerImpl implements Player {

    private int health = 100;

    @Override
    public void showDeathAnimation() {
        System.out.println("Death animation");
    }

    @Override
    public int getHealth() {
        return health;
    }

    @Override
    public int addHealth(int amount) {
        health += amount;
        return health;
    }
}

public class ExceptionPlayerImpl implements Player {

    @Override
    public void showDeathAnimation() {
        throw new IllegalStateException("Animation failed");
    }

    @Override
    public int getHealth() {
        throw new IllegalStateException("Health lookup failed");
    }

    @Override
    public int addHealth(int amount) {
        throw new UnsupportedOperationException();
    }
}

@NativeLibrary("../native/player")
interface PlayerC {
    @NativeSymbol("test_player")
    int testPlayer(MemorySegment segment);

    @NativeSymbol("test_player_exception")
    int testPlayerException(MemorySegment segment);

    @NativeSymbol("test_player_concurrency")
    int testPlayerConcurrency(MemorySegment player);
}

@Test
void main() {
    System.out.println("======[JvmTest]======");
    var library = FFM.load(PlayerC.class);

    try (Arena arena = Arena.ofConfined()) {
        Player player = new PlayerImpl();

        MemorySegment segment = FFM.export(
                Player.class,
                player,
                arena
        );

        int result = library.testPlayer(segment);

        System.out.println(result);
    }
}

@Test
void testJvmStructureExceptionHandling() {
    var library = FFM.load(PlayerC.class);
    try (Arena arena = Arena.ofConfined()) {
        Player player = new ExceptionPlayerImpl();

        MemorySegment segment = FFM.export(
                Player.class,
                player,
                arena
        );

        assertEquals(0, library.testPlayerException(segment));
    }
}

@Test
void testJvmStructureArenaLifetime() {
    var library = FFM.load(PlayerC.class);
    Arena arena = Arena.ofConfined();

    MemorySegment segment = FFM.export(
            Player.class,
            new PlayerImpl(),
            arena
    );

    arena.close();

    assertThrows(
            IllegalStateException.class,
            () -> library.testPlayer(segment)
    );
}

@Test
void testJvmStructureConcurrency() throws Exception {
    var library = FFM.load(PlayerC.class);

    try (Arena arena = Arena.ofShared()) {
        Player player = new PlayerImpl();

        MemorySegment segment = FFM.export(
                Player.class,
                player,
                arena
        );

        int threadCount = 8;
        int iterations = 10_000;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        try {
            List<Future<Long>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    long result = 0;

                    /*for (int j = 0; j < iterations; j++) {
                        result += library.testPlayer(segment);
                    }*/

                    for (int j = 0; j < iterations; j++) {
                        result += library.testPlayerConcurrency(segment);
                    }

                    return result;
                }));
            }

            for (Future<Long> future : futures) {
                assertEquals(100L * iterations, future.get());
            }
        } finally {
            executor.shutdown();
        }
    }
}