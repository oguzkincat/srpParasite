package com.parasitemimic.dialogue;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import java.util.Random;

/**
 * Реплики Исказителя в стиле Махито: душа, форма, биомасса.
 */
public final class MahitoDialogue {

    private static final Random RNG = new Random();

    private static final String[] SPAWN = {
            "Интересно... новая душа. Давай посмотрим, на что она способна.",
            "Форма человека такая хрупкая. Я лишь немного... поправлю её.",
            "Не бойся. Боль — это просто способ познакомиться с собственной душой.",
            "О, живой. Как мило. Давай сыграем.",
            "Твоя душа уже дрожит. Чувствуешь?"
    };

    private static final String[] SPAWN_FROM_FLESH = {
            "Биомасса собралась... и вот я.",
            "Четыре куска плоти — одна душа. Элегантная арифметика.",
            "Вы растили биомассу, а получили меня. Не благодарите.",
            "Плоть ещё тёплая. Удобно.",
            "Сколько биомассы нужно, чтобы слепить человека? Меньше, чем ты думаешь.",
            "Движущаяся плоть хотела стать кем-то. Я любезно согласился.",
            "Фаза четвёртая. Биомасса наконец научилась лгать, что она — человек."
    };

    private static final String[] ATTACK = {
            "Душа и тело — одно целое. Позволь мне это доказать.",
            "Кричи громче. Так интереснее читать твою душу.",
            "Ты всё ещё цепляешься за эту форму? Забавно.",
            "Не волнуйся. Я сделаю тебя... другим.",
            "Боль — лучший учитель. Разве ты не согласен?",
            "Ха-ха... вот так. Ещё немного.",
            "Отдай биомассу. Добровольно или нет — без разницы.",
            "Твоя биомасса кричит громче, чем ты."
    };

    private static final String[] STRENGTH_BURST = {
            "Теперь я серьёзен.",
            "Почувствуй, как меняется форма.",
            "Твоя душа уже не твоя.",
            "Искажение... начинается.",
            "Биомасса внутри тебя уже слушается меня."
    };

    private static final String[] KILL = {
            "Скучно. Душа оказалась пустой.",
            "Всё? Я ожидал большего.",
            "Форма разрушена. Душа... тоже.",
            "Следующий. Надеюсь, будет интереснее.",
            "Прощай. Ты был... посредственным материалом.",
            "Биомасса собрана. Душа утилизирована.",
            "Ещё одна порция. Улей скажет спасибо."
    };

    private static final String[] HURT = {
            "Ох? Ты можешь сопротивляться. Хорошо.",
            "Боль... даже мне бывает любопытно.",
            "Неплохо. Но этого мало, чтобы изменить меня.",
            "Продолжай. Я изучаю тебя.",
            "Рвёшь биомассу? Я просто возьму её обратно."
    };

    private static final String[] ROAR = {
            "Все вы одинаковы внутри.",
            "Покажи свою настоящую форму!",
            "Души вокруг... такие громкие.",
            "Слышишь? Биомасса зовёт."
    };

    private static final String[] IDLE = {
            "Человеческие души... такие скучные, когда молчат.",
            "Интересно, какой формы ты станешь, если я дотронусь глубже?",
            "Мир полон материала. Осталось только... поиграть.",
            "Биомасса вокруг... такая послушная.",
            "Каждая капля биомассы — чья-то неудачная душа.",
            "Не жалей плоть. Она всё равно хотела стать мной.",
            "Улей копит биомассу. Я лишь... придаю ей вкус."
    };

    private MahitoDialogue() {}

    public static void sayNear(World world, double x, double y, double z, double radius, String[] pool) {
        if (world.isRemote || pool == null || pool.length == 0) {
            return;
        }
        String line = pool[RNG.nextInt(pool.length)];
        String message = TextFormatting.DARK_PURPLE + "[Исказитель] " + TextFormatting.LIGHT_PURPLE + line;

        for (EntityPlayer player : world.playerEntities) {
            if (player.getDistanceSq(x, y, z) <= radius * radius) {
                player.sendMessage(new TextComponentString(message));
            }
        }
    }

    public static void onSpawn(World world, double x, double y, double z) {
        sayNear(world, x, y, z, 24.0D, SPAWN);
    }

    public static void onSpawnFromFlesh(World world, double x, double y, double z) {
        sayNear(world, x, y, z, 28.0D, SPAWN_FROM_FLESH);
    }

    public static void onAttack(World world, double x, double y, double z) {
        if (RNG.nextFloat() < 0.35F) {
            sayNear(world, x, y, z, 16.0D, ATTACK);
        }
    }

    public static void onStrengthBurst(World world, double x, double y, double z) {
        sayNear(world, x, y, z, 20.0D, STRENGTH_BURST);
    }

    public static void onKill(World world, double x, double y, double z) {
        sayNear(world, x, y, z, 20.0D, KILL);
    }

    public static void onHurt(World world, double x, double y, double z) {
        if (RNG.nextFloat() < 0.25F) {
            sayNear(world, x, y, z, 16.0D, HURT);
        }
    }

    public static void onRoar(World world, double x, double y, double z) {
        sayNear(world, x, y, z, 18.0D, ROAR);
    }

    public static void onIdle(World world, double x, double y, double z) {
        if (RNG.nextFloat() < 0.02F) {
            sayNear(world, x, y, z, 12.0D, IDLE);
        }
    }
}
