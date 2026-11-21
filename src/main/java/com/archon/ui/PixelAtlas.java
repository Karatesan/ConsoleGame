package com.archon.ui;

import com.archon.model.Entity;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * Procedural 16x16 roguelike sprite atlas baked in memory at startup.
 * ATLAS_W x ATLAS_H = 256 x 80 (16x5 cells). Cell 68 is a solid white pixel block for Batch FX.
 */
public final class PixelAtlas {
    public static final int CELL = 16, COLS = 16, ROWS = 5;
    public static final int ATLAS_W = COLS * CELL, ATLAS_H = ROWS * CELL;

    public static final int POSE_IDLE = 0, POSE_MOVE = 1, POSE_ATTACK = 2,
            POSE_CAST = 3, POSE_GUARD = 4, POSE_HURT = 5;

    public static final int
            FLOOR_0 = 0, FLOOR_1 = 1, WALL_TOP = 2, WALL_FRONT = 3,
            WATER_0 = 4, WATER_1 = 5, WATER_2 = 6, WATER_3 = 7,
            ACID_0 = 8, ACID_1 = 9, ACID_2 = 10, ACID_3 = 11,
            OIL_SLICK = 12, ICE_TILE = 13, TAR_TILE = 14,
            FIRE_0 = 15, FIRE_1 = 16, FIRE_2 = 17, FIRE_3 = 18, GAS_PUFF = 19,
            THRALL_0 = 20, THRALL_1 = 21, THRALL_2 = 22, THRALL_3 = 23,
            ORC_0 = 24, ORC_1 = 25, ORC_2 = 26, ORC_3 = 27,
            GOBLIN_0 = 28, GOBLIN_1 = 29, GOBLIN_2 = 30, GOBLIN_3 = 31,
            OGRE_0 = 32, OGRE_1 = 33, OGRE_2 = 34, OGRE_3 = 35,
            RAT_0 = 36, RAT_1 = 37, RAT_2 = 38, RAT_3 = 39,
            BRAZIER_0 = 40, BRAZIER_1 = 41, BRAZIER_2 = 42, BRAZIER_3 = 43,
            BARREL = 44, CRATE = 45, PILLAR = 46, DOOR = 47,
            LOOT_BLADE = 48, LOOT_OIL = 49, LOOT_WATER = 50, LOOT_ACID = 51, LOOT_STONE = 52,
            BRAZIER_OFF = 53,
            THRALL_MOVE_0 = 54, THRALL_MOVE_1 = 55, THRALL_MOVE_2 = 56, THRALL_MOVE_3 = 57,
            THRALL_ATTACK_0 = 58, THRALL_ATTACK_1 = 59, THRALL_ATTACK_2 = 60, THRALL_ATTACK_3 = 61,
            THRALL_CAST_0 = 62, THRALL_CAST_1 = 63, THRALL_CAST_2 = 64, THRALL_CAST_3 = 65,
            THRALL_GUARD = 66, THRALL_HURT = 67,
            COUNT = 68;

    private static final String[][] SPRITES = {
            // 0: FLOOR_0
            {
                    "dddddddddddddddd",
                    "dccccccddccccccd",
                    "dccccccddccccccd",
                    "dcCccccddccccccd",
                    "dccccccddccccdcd",
                    "dccccccddccccccd",
                    "dccccccddccccccd",
                    "dddddddddddddddd",
                    "dddddddddddddddd",
                    "dcccdddccccccddd",
                    "dcccdddccccccddd",
                    "dcccdddcCccccddd",
                    "dcccdddccccccddd",
                    "dcdcdddccccccddd",
                    "dcccdddccccccddd",
                    "dddddddddddddddd"
            },
            // 1: FLOOR_1
            {
                    "dddddddddddddddd",
                    "dccccccddccccccd",
                    "dccdcccddccccccd",
                    "dccccccddcCccccd",
                    "dccccccddccccccd",
                    "dccccccddccccccd",
                    "dccccccddccccccd",
                    "dddddddddddddddd",
                    "dddddddddddddddd",
                    "dccccccdddcccccd",
                    "dcCccccdddcccccd",
                    "dccccccdddccdccd",
                    "dccccccdddcccccd",
                    "dccccccdddcccccd",
                    "dccccccdddcccccd",
                    "dddddddddddddddd"
            },
            // 2: WALL_TOP
            {
                    "kkkkkkkkkkkkkkkk",
                    "kssssssssssssssk",
                    "kssSssssssSssssk",
                    "kssssssssssssssk",
                    "ksddddddddddddsk",
                    "ksddddsssssssssk",
                    "ksddddskkddddssk",
                    "ksddddskkddddssk",
                    "ksddddsssssssddk",
                    "kssddssssssddddk",
                    "kssssssssssddddk",
                    "kssSsssssssddddk",
                    "kssssssssssssssk",
                    "kddddddddddddddk",
                    "kcccccccccccccck",
                    "kkkkkkkkkkkkkkkk"
            },
            // 3: WALL_FRONT
            {
                    "kkkkkkkkkkkkkkkk",
                    "kSSSSSSSSSSSSSSk",
                    "kssssssssssssssk",
                    "kddddddddddddddk",
                    "kccccckccccccckk",
                    "kccccdkccccccdkk",
                    "kdddddkdddddddkk",
                    "kccdkccccccdkcck",
                    "kccdkccccccdkcck",
                    "kdddkdddddddkddk",
                    "kccccckccccccckk",
                    "kccccdkccccccdkk",
                    "kdddddkdddddddkk",
                    "kccdkccccccdkcck",
                    "kddddddddddddddk",
                    "kkkkkkkkkkkkkkkk"
            },
            // 4: WATER_0
            {
                    "................",
                    "..bbbbbbbbbbbb..",
                    ".bBBbbbbbbbbbBb.",
                    ".bbbbwwbbbbbBbb.",
                    ".bbbwwwwbbbbbbb.",
                    ".bbbbbbbbbbbbbb.",
                    ".bbBbbbbbbwwbbb.",
                    ".bbbbbbbbwwwwbb.",
                    ".bbbbwbbbbbbbbb.",
                    ".bbbwwwbbbbbbbb.",
                    ".bbbbbbbbbbBbbb.",
                    ".bbBbbbbbbbBbbb.",
                    ".bbbbbbbbbbbbbb.",
                    ".bbbbbbbbbbbbbb.",
                    "..bbbbbbbbbbbb..",
                    "................"
            },
            // 5: WATER_1
            {
                    "................",
                    "..bbbbbbbbbbbb..",
                    ".bbbBBbbbbbbbbb.",
                    ".bbbbbbwwbbbbBb.",
                    ".bbbbbwwwwbbbBb.",
                    ".bbbbbbbbbbbbbb.",
                    ".bBbbbbbbbbbwwb.",
                    ".bbbbbbbbbbwwwb.",
                    ".bbbbbbwbbbbbbb.",
                    ".bbbbbwwwbbbbbb.",
                    ".bBBbbbbbbbbBbb.",
                    ".bbbbbbbbbbbbbb.",
                    ".bbBbbbbbbbbbbb.",
                    ".bbbbbbbbbbbbbb.",
                    "..bbbbbbbbbbbb..",
                    "................"
            },
            // 6: WATER_2
            {
                    "................",
                    "..bbbbbbbbbbbb..",
                    ".bbbbbBBbbbbbbb.",
                    ".bbbbbbbbwwbbbb.",
                    ".bbbbbbbwwwwbbb.",
                    ".bBbbbbbbbbbbbb.",
                    ".bbwwbbbbbbbbbb.",
                    ".bwwwwbbbbBbbbb.",
                    ".bbbbbbbbwbbbbb.",
                    ".bbbbbbbwwwbbbb.",
                    ".bbbbBBbbbbbbBb.",
                    ".bbbbbbbbbbbbbb.",
                    ".bbbbBbbbbbbbbb.",
                    ".bbbbbbbbbbbbbb.",
                    "..bbbbbbbbbbbb..",
                    "................"
            },
            // 7: WATER_3
            {
                    "................",
                    "..bbbbbbbbbbbb..",
                    ".bbbbbbbbBBbbbb.",
                    ".bbwwbbbbbbBbbb.",
                    ".bwwwwbbbbbbbbb.",
                    ".bbbbbbbbbbbbbb.",
                    ".bbbbwwbbbbBbbb.",
                    ".bbbwwwwbbbbbbb.",
                    ".bbbbbbbbbbwbbb.",
                    ".bbbbbbbbbwwwbb.",
                    ".bbBbbbbbbbbbbb.",
                    ".bbBbbbbBBbbbbb.",
                    ".bbbbbbbbbbbbbb.",
                    ".bbbbbbbbbbbbbb.",
                    "..bbbbbbbbbbbb..",
                    "................"
            },
            // 8: ACID_0
            {
                    "................",
                    "..aaaaaaaaaaaa..",
                    ".aAAaaaaaaaAAaa.",
                    ".aaaLLaaaaaaaaa.",
                    ".aaLLLLaaaaaaaa.",
                    ".aaaLLaaaaaaaaa.",
                    ".aaaaaaaLLaaaaa.",
                    ".aaaaaaLLLLaaaa.",
                    ".aaAaaaaLLaaaaa.",
                    ".aaaaaaaaaaLLaa.",
                    ".aaaaaaaaaLLLLa.",
                    ".aaaaLLaaaaLLaa.",
                    ".aaaLLLLaaaaaaa.",
                    ".aaaaLLaaaaaaaa.",
                    "..aaaaaaaaaaaa..",
                    "................"
            },
            // 9: ACID_1
            {
                    "................",
                    "..aaaaaaaaaaaa..",
                    ".aaALLaaaaaAAaa.",
                    ".aaLaaLaaaaaaaa.",
                    ".aaaAAaaaaaaaaa.",
                    ".aaaaaaaLLaaaaa.",
                    ".aaaaaaLLLLaaaa.",
                    ".aaaaaaaLLaaaaa.",
                    ".aaaaaaaaaaLLaa.",
                    ".aaaaaaaaaLaaLa.",
                    ".aaaaLLaaaaAAaa.",
                    ".aaaLLLLaaaaaaa.",
                    ".aaaaLLaaaaAAaa.",
                    ".aaaaaaaaaALLAa.",
                    "..aaaaaaaaaaaa..",
                    "................"
            },
            // 10: ACID_2
            {
                    "................",
                    "..aaaaaaaaaaaa..",
                    ".aaaAAaaaaaaaaa.",
                    ".aaaaaaaaLLaaaa.",
                    ".aaaaaaALaaLAaa.",
                    ".aaaaaaaaAAaaaa.",
                    ".aaaaaaaaaaaaAa.",
                    ".aaAaaaaaAAaaaa.",
                    ".aaaaLLaaaaaaaa.",
                    ".aaaLaaLaaaaaaa.",
                    ".aaaaAAaaaaLLaa.",
                    ".aaaaaaaaaLLLLa.",
                    ".aaAAaaaaaaLLaa.",
                    ".aaLLaaaaaaaaaa.",
                    "..aaaaaaaaaaaa..",
                    "................"
            },
            // 11: ACID_3
            {
                    "................",
                    "..aaaaaaaaaaaa..",
                    ".aaaaaaaaAAaaaa.",
                    ".aaaAAaaaaaaaaa.",
                    ".aaALLAaAAaaaaa.",
                    ".aaaLLaaaaaaaaa.",
                    ".aaaaaaaAAaaaaa.",
                    ".aaaaaaaLLaaaaa.",
                    ".aaaaaaaAAaaaaa.",
                    ".aaaaaaaaaALLAa.",
                    ".aaaaAAaaaLLLLa.",
                    ".aaaLLLLaaaLLaa.",
                    ".aaaLLLLaaaaaaa.",
                    ".aaaaAAaaaaaaaa.",
                    "..aaaaaaaaaaaa..",
                    "................"
            },
            // 12: OIL_SLICK
            {
                    "................",
                    "...oooooooooo...",
                    "..oOOooooooooo..",
                    ".ooOOoooooooooo.",
                    ".oooooooooYYooo.",
                    ".oooooooooYoooo.",
                    ".ooooOOoooooooo.",
                    ".oooOOooooooooo.",
                    ".oooooooooooooo.",
                    ".ooooooooOOoooo.",
                    ".ooYoooooOOoooo.",
                    ".ooYYoooooooooo.",
                    ".oooooooooooooo.",
                    "..oooooooooooo..",
                    "...oooooooooo...",
                    "................"
            },
            // 13: ICE_TILE
            {
                    "................",
                    ".iiiiiiwwiiiiii.",
                    ".iiiiiwwwiiiiii.",
                    ".iiiiwwiiiiiwwi.",
                    ".iiiwwiiiiiwwii.",
                    ".iiwwiiIIIwwiii.",
                    ".iwwiiIIIwwiiii.",
                    ".iiiiIIIwwiiiii.",
                    ".iiiIIIwwiiiwwi.",
                    ".iiwwwwwiiiwwii.",
                    ".iwwwwwiiiwwiii.",
                    ".iiwwiiiiwwiiii.",
                    ".iwwiiiiwwiiiii.",
                    ".iiiiiiiiiiiiii.",
                    ".iiiiiwwwwiiiii.",
                    "................"
            },
            // 14: TAR_TILE
            {
                    "................",
                    "..oooooooooooo..",
                    ".ooaaaaaaaaaooo.",
                    ".oaoooooooooaoo.",
                    ".ooOOoooLLoooao.",
                    ".oooaooLLLLoooo.",
                    ".ooaaoooLLooooo.",
                    ".oooooooaoooooo.",
                    ".ooLLoooooooooo.",
                    ".oLLLLoooOOoooo.",
                    ".ooLLooooaooLLo.",
                    ".ooooaaaoooLLLo.",
                    ".oooooooooooLoo.",
                    ".ooaooooooooooo.",
                    "..oooooooooooo..",
                    "................"
            },
            // 15: FIRE_0
            {
                    ".......f........",
                    "......fYf.......",
                    ".....fYYf...f...",
                    "..f..fYYf..fYf..",
                    ".fYf.fYYf..fYf..",
                    ".fYf.fYYff.fYf..",
                    ".fYYffYYYffYYf..",
                    ".ffYYfYYYYYYf...",
                    "..fYYYYYYYYYf...",
                    "..fYYWWWWYYYf...",
                    "..ffYWWWWYYff...",
                    "...fYWWWWYYf....",
                    "...fYWWWWYf.....",
                    "....fYYYYf......",
                    ".....ffff.......",
                    "................"
            },
            // 16: FIRE_1
            {
                    "....f...........",
                    "...fYf.....f....",
                    "...fYYf...fYf...",
                    "...fYYf...fYYf..",
                    ".f..fYYf.fYYYf..",
                    "fYf.fYYfffYYf...",
                    "fYYffYYYYYYff...",
                    ".fYYYYYYYYYf....",
                    ".fYYWWWWYYYf....",
                    ".fYYWWWWYYYf....",
                    "..fYWWWWYYff....",
                    "..fYWWWWYYf.....",
                    "...fWWWWYf......",
                    "....fYYYf.......",
                    ".....ffff.......",
                    "................"
            },
            // 17: FIRE_2
            {
                    "........f.......",
                    "....f..fYf......",
                    "...fYffYYYf.....",
                    "...fYYfYYYf..f..",
                    "..fYYYfYYff.fYf.",
                    "..fYYffYYff.fYf.",
                    ".fYYYfYYYYffYYf.",
                    ".fYYYYYYYYYYYf..",
                    "..fYYWWWWYYYf...",
                    "..fYWWWWWWYYf...",
                    "..ffYWWWWYYff...",
                    "...fYWWWWYYf....",
                    "...fYYWWYYf.....",
                    "....fYYYYf......",
                    ".....ffff.......",
                    "................"
            },
            // 18: FIRE_3
            {
                    "..........f.....",
                    ".....f...fYf....",
                    "....fYf..fYYf...",
                    "...fYYf..fYYf...",
                    ".f.fYYYffYYYf...",
                    "fYffYYYffYYff...",
                    ".fYYYYYYYYYYf...",
                    "..fYYYYYYYYff...",
                    "..fYYYWWWWYf....",
                    "..fYYYWWWWYf....",
                    "..ffYYWWWWff....",
                    "...fYYWWWWf.....",
                    "...fYWWWWYf.....",
                    "....fYYYYf......",
                    ".....ffff.......",
                    "................"
            },
            // 19: GAS_PUFF
            {
                    "................",
                    "....wwwwwwww....",
                    "..wwwwWWWWwwww..",
                    ".wwwWWWWWWWWwww.",
                    ".wwWWWWWWWWWWww.",
                    "wwwWWWWWWWWWWwww",
                    "wwWWWWWWWWWWWWww",
                    "wwWWWWWWWWWWWWww",
                    "wwwWWWWWWWWWWwww",
                    ".wwWWWWWWWWWWww.",
                    ".wwwWWWWWWWWwww.",
                    "..wwwwWWWWwwww..",
                    "....wwwwwwww....",
                    "................",
                    "................",
                    "................"
            },
            // 20: THRALL_0
            {
                    "......kkkk......",
                    ".....kTTTTk.....",
                    "....kTTttTTk....",
                    "....kTkkkkTk....",
                    "....kTkCCkTk....",
                    ".f..kTkkkkTk....",
                    "fYf.kkttttkk....",
                    "fYfkttTTTTttk.s.",
                    "kWkktTTTTTTtkss.",
                    "kwkkttTTTTttks..",
                    "kwk.kttttttksk..",
                    "kdk.kttttttkk...",
                    ".k..kttkkttk....",
                    "....kttk.kttk...",
                    "....kddk.kddk...",
                    "....kkkk.kkkk..."
            },
            // 21: THRALL_1
            {
                    "......kkkk......",
                    ".....kTTTTk.....",
                    "....kTTttTTk....",
                    "..f.kTkkkkTk....",
                    ".fYfkTkCCkTk....",
                    "fYYfkTkkkkTk....",
                    "fWYfkkttttkk..S.",
                    "kWkkttTTTTttkSS.",
                    "kwkktTTTTTTtkss.",
                    "kwkkttTTTTttks..",
                    "kdk.kttttttksk..",
                    ".k..kttttttkk...",
                    "....kttkkttk....",
                    "....kttk.kttk...",
                    "....kddk.kddk...",
                    "....kkkk.kkkk..."
            },
            // 22: THRALL_2
            {
                    "................",
                    "......kkkk......",
                    ".....kTTTTk.....",
                    ".f..kTTttTTk....",
                    "fYf.kTkkkkTk....",
                    "fYffkTkCCkTk....",
                    "kWkkkTkkkkTk....",
                    "kwkk.kkttttk.s..",
                    "kwkkttTTTTtkss..",
                    "kdkktTTTTTTtksS.",
                    ".k.kttTTTTttks..",
                    "....kttttttkksk.",
                    "....kttkkttk....",
                    "....kttk.kttk...",
                    "....kddk.kddk...",
                    "....kkkk.kkkk..."
            },
            // 23: THRALL_3
            {
                    "................",
                    "......kkkk......",
                    ".....kTTTTk.....",
                    "....kTTttTTk....",
                    ".f..kTkkkkTk....",
                    "fYf.kTkCCkTk....",
                    "fWffkTkkkkTk....",
                    "kWkk.kkttttk.s..",
                    "kwkkttTTTTtkss..",
                    "kwkktTTTTTTtks..",
                    "kdkkttTTTTttkk..",
                    ".k..kttttttksk..",
                    "....kttkkttk....",
                    "....kttk.kttk...",
                    "....kddk.kddk...",
                    "....kkkk.kkkk..."
            },
            // 24: ORC_0
            {
                    ".....kkkkkk.....",
                    "....kggggggk....",
                    "...kgwggggwgk...",
                    "...kgkggggkgk...",
                    "...kggggggggk...",
                    "...kggwkkwggk...",
                    "..ksskkkkkkssk..",
                    ".ksssssSSsssssk.",
                    ".ksssksSSsksssk.",
                    ".kggkksSSskkggk.",
                    ".kggksssssskgsk.",
                    "..kk.kssssk.ks..",
                    ".....kssskk..s..",
                    "....ksskkssk....",
                    "....kddkkddk....",
                    "....kkkkkkkk...."
            },
            // 25: ORC_1
            {
                    ".....kkkkkk.....",
                    "....kggggggk....",
                    "...kgwggggwgk...",
                    "...kgkggggkgk...",
                    "...kggggggggk...",
                    "...kggwkkwggk...",
                    "..ksskkkkkkssk..",
                    ".kssssSSSSssssk.",
                    ".ksssksSSsksssk.",
                    ".kggkksSSskkggk.",
                    ".kggksssssskgSk.",
                    "..kk.kssssk.kS..",
                    ".....kssskk.....",
                    "....ksskkssk....",
                    "....kddkkddk....",
                    "....kkkkkkkk...."
            },
            // 26: ORC_2
            {
                    "................",
                    ".....kkkkkk.....",
                    "....kggggggk....",
                    "...kgwggggwgk...",
                    "...kgkggggkgk...",
                    "...kggggggggk...",
                    "...kggwkkwggk...",
                    "..ksskkkkkkssk..",
                    ".ksssssSSsssssk.",
                    ".ksssksSSsksssk.",
                    ".kggkksSSskkggk.",
                    ".kggksssssskgsk.",
                    "..kk.kssssk..s..",
                    "....ksskkssk.s..",
                    "....kddkkddk....",
                    "....kkkkkkkk...."
            },
            // 27: ORC_3
            {
                    "................",
                    ".....kkkkkk.....",
                    "....kggggggk....",
                    "...kgwggggwgk...",
                    "...kgkggggkgk...",
                    "...kggggggggk...",
                    "...kggwkkwggk...",
                    "..ksskkkkkkssk..",
                    ".kssssSSSSssssk.",
                    ".ksssksSSsksssk.",
                    ".kggkksSSskkggk.",
                    ".kggksssssskgsk.",
                    "..kk.kssssk.ks..",
                    "....ksskkssk....",
                    "....kddkkddk....",
                    "....kkkkkkkk...."
            },
            // 28: GOBLIN_0
            {
                    "................",
                    "....kkkkkkkk....",
                    "..kkGGGGGGGGkk..",
                    ".kGGGGGGGGGGGGk.",
                    "..kkGGkGGkGGkk..",
                    "....kGwkGwkGk...",
                    "....kGGGGGGkk...",
                    "...kkddddddkkb..",
                    "..kddddddddddkb.",
                    "..kGGkddddkGGkb.",
                    "...kk.kddk.ksks.",
                    "......kddk..kb..",
                    ".....kdkkdk..b..",
                    ".....kdkkdk.....",
                    ".....kkkkkk.....",
                    "................"
            },
            // 29: GOBLIN_1
            {
                    "................",
                    ".kk.kkkkkkkk.kk.",
                    ".kGGGGGGGGGGGGk.",
                    "..kkGGGGGGGGkk..",
                    "...kGGkGGkGGk...",
                    "....kGwkGwkGk...",
                    "....kGGGGGGkkb..",
                    "...kkddddddkkkb.",
                    "..kddddddddddkb.",
                    "..kGGkddddkGGkbS",
                    "...kk.kddk.ksks.",
                    "......kddk..kb..",
                    ".....kdkkdk.....",
                    ".....kdkkdk.....",
                    ".....kkkkkk.....",
                    "................"
            },
            // 30: GOBLIN_2
            {
                    "................",
                    "................",
                    "....kkkkkkkk....",
                    "..kkGGGGGGGGkk..",
                    ".kGGGGGGGGGGGGk.",
                    "..kkGGkGGkGGkk..",
                    "....kGwkGwkGk...",
                    "....kGGGGGGkkb..",
                    "...kkddddddkkkb.",
                    "..kddddddddddkb.",
                    "..kGGkddddkGGkbs",
                    "...kk.kddk.ksks.",
                    ".....kdkkdk..kb.",
                    ".....kdkkdk.....",
                    ".....kkkkkk.....",
                    "................"
            },
            // 31: GOBLIN_3
            {
                    "................",
                    "................",
                    "....kkkkkkkk....",
                    "..kkGGGGGGGGkk..",
                    "..kGGGGGGGGGGk..",
                    ".kGGkkGGkGGkkGk.",
                    "....kGwkGwkGk...",
                    "....kGGGGGGkk...",
                    "...kkddddddkkb..",
                    "..kddddddddddkb.",
                    "..kGGkddddkGGkb.",
                    "...kk.kddk.kskS.",
                    ".....kdkkdk..kb.",
                    ".....kdkkdk..b..",
                    ".....kkkkkk.....",
                    "................"
            },
            // 32: OGRE_0
            {
                    "...kkkkkkkkkk...",
                    "..koooooooooook.",
                    ".kooookkkkoooook",
                    ".kookrkkkkkrokok",
                    ".koooowkkwoooook",
                    ".kkoooooooooookk",
                    "koooddddddddooob",
                    "koooddddddddookb",
                    "koodddddddddddkb",
                    "koodddddddddddkb",
                    "kookddddddddkkbb",
                    ".kk.kddkkddk.kbb",
                    "....kddkkddk..k.",
                    "....kddkkddk....",
                    "....kkkkkkkk....",
                    "................"
            },
            // 33: OGRE_1
            {
                    "...kkkkkkkkkk...",
                    "..koooooooooook.",
                    ".kooookkkkoooook",
                    ".kookrkkkkkrokok",
                    ".koooowkkwoooook",
                    ".kkoooooooooookb",
                    "koooddddddddoobB",
                    "koooddddddddookB",
                    "koodddddddddddkb",
                    "koodddddddddddkb",
                    "kookddddddddkkb.",
                    ".kk.kddkkddk.k..",
                    "....kddkkddk....",
                    "....kddkkddk....",
                    "....kkkkkkkk....",
                    "................"
            },
            // 34: OGRE_2
            {
                    "................",
                    "...kkkkkkkkkk...",
                    "..koooooooooook.",
                    ".kooookkkkoooook",
                    ".kookrkkkkkrokok",
                    ".koooowkkwoooook",
                    ".kkoooooooooookk",
                    "koooddddddddoook",
                    "koooddddddddookb",
                    "koodddddddddddkb",
                    "kookddddddddkkbb",
                    ".kk.kddkkddkkbBb",
                    "....kddkkddk.kbb",
                    "....kddkkddk..k.",
                    "....kkkkkkkk....",
                    "................"
            },
            // 35: OGRE_3
            {
                    "................",
                    "...kkkkkkkkkk...",
                    "..koooooooooook.",
                    ".kooookkkkoooook",
                    ".kookrkkkkkrokok",
                    ".koooowkkwoooook",
                    ".kkoooooooooookk",
                    "koooddddddddooob",
                    "koooddddddddookb",
                    "koodddddddddddkb",
                    "kookddddddddkkbB",
                    ".kk.kddkkddk.kbb",
                    "....kddkkddk.kb.",
                    "....kddkkddk....",
                    "....kkkkkkkk....",
                    "................"
            },
            // 36: RAT_0
            {
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "...kk....kk.....",
                    "..kppk..kppk....",
                    "..ksskkkkssk....",
                    "..kssrksrkssk...",
                    "..ksssssssssskk.",
                    "...ksssssssssssk",
                    "....ksssssssskpk",
                    "....kssk..ksskkp",
                    ".....kk....kk.p.",
                    "................"
            },
            // 37: RAT_1
            {
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "...kk....kk.....",
                    "..kppk..kppk....",
                    "..ksskkkkssk....",
                    "..kssrksrkSsk...",
                    "..ksssssssssskkp",
                    "...ksssssssssskp",
                    "....ksssssssskp.",
                    "....kssk..kssk..",
                    ".....kk....kk...",
                    "................"
            },
            // 38: RAT_2
            {
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "...kk....kk.....",
                    "..kppk..kppk....",
                    "..ksskkkkssk....",
                    "..kssrksrkssk...",
                    "..ksssssssssskk.",
                    "...ksssssssssspk",
                    "....ksskssskkkpp",
                    ".....kk..kk.....",
                    "................"
            },
            // 39: RAT_3
            {
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "..kk......kk....",
                    "..kppk..kppk....",
                    "..ksskkkkssk....",
                    "..kssrksrkssk...",
                    "..ksssssssssskk.",
                    "...ksssssssssssk",
                    "....ksskssskkpk.",
                    ".....kk..kk..pp.",
                    "................"
            },
            // 40: BRAZIER_0
            {
                    ".......f........",
                    "......fYf.......",
                    ".....fYYf..f....",
                    "....fYYYYffYf...",
                    "....fYYWWYYYf...",
                    ".....fYWWYYf....",
                    "...kkkkkkkkkk...",
                    "..kssssssssssk..",
                    "..ksSSSSSSSSsk..",
                    "...kssssssssk...",
                    "....ksskkssk....",
                    "....ksk..ksk....",
                    "...kssk..kssk...",
                    "...ksk....ksk...",
                    "..kkkk....kkkk..",
                    "................"
            },
            // 41: BRAZIER_1
            {
                    ".....f..........",
                    "....fYf....f....",
                    "....fYYf..fYf...",
                    "...fYYYYffYYf...",
                    "....fYWWYYYYf...",
                    ".....fWWYYYf....",
                    "...kkkkkkkkkk...",
                    "..kssssssssssk..",
                    "..ksSSSSSSSSsk..",
                    "...kssssssssk...",
                    "....ksskkssk....",
                    "....ksk..ksk....",
                    "...kssk..kssk...",
                    "...ksk....ksk...",
                    "..kkkk....kkkk..",
                    "................"
            },
            // 42: BRAZIER_2
            {
                    "........f.......",
                    "...f...fYf......",
                    "......fYYYf.f...",
                    "....ffYYYYffYf..",
                    "....fYYWWWYYYf..",
                    ".....fYWWWYYf...",
                    "...kkkkkkkkkk...",
                    "..kssssssssssk..",
                    "..ksSSSSSSSSsk..",
                    "...kssssssssk...",
                    "....ksskkssk....",
                    "....ksk..ksk....",
                    "...kssk..kssk...",
                    "...ksk....ksk...",
                    "..kkkk....kkkk..",
                    "................"
            },
            // 43: BRAZIER_3
            {
                    "..........f.....",
                    ".....f...fYf....",
                    "....fYf..fYYf...",
                    "...fYYfffYYYf...",
                    "....fYYYWWYYf...",
                    ".....fYYWWYf....",
                    "...kkkkkkkkkk...",
                    "..kssssssssssk..",
                    "..ksSSSSSSSSsk..",
                    "...kssssssssk...",
                    "....ksskkssk....",
                    "....ksk..ksk....",
                    "...kssk..kssk...",
                    "...ksk....ksk...",
                    "..kkkk....kkkk..",
                    "................"
            },
            // 44: BARREL
            {
                    "................",
                    "...kkkkkkkkkk...",
                    "..kbbbbbbbbbbk..",
                    ".kbBBbbbbbbBBbk.",
                    ".kssssssssssssk.",
                    ".kbBBbOObbBBbbk.",
                    ".kbbOOOObbbbbdk.",
                    ".kbbbOObbbbbbdk.",
                    ".kbbbbbbbbbbbdk.",
                    ".kssssssssssssk.",
                    ".kbBBbbbbbbdbdk.",
                    ".kbBBbbbbbbdbdk.",
                    ".kbbbbbbbbbbbdk.",
                    "..kddddddddddk..",
                    "...kkkkkkkkkk...",
                    "................"
            },
            // 45: CRATE
            {
                    "................",
                    ".kkkkkkkkkkkkkk.",
                    ".kBBBBBBBBBBBBk.",
                    ".kBbbbbbbbbbbdk.",
                    ".kBbbkkkkkkbbdk.",
                    ".kBbbkBBBBkbbdk.",
                    ".kBbbbkBBkbbbdk.",
                    ".kBbbbbkkbbbbdk.",
                    ".kBbbbbkkbbbbdk.",
                    ".kBbbbkBBkbbbdk.",
                    ".kBbbkBBBBkbbdk.",
                    ".kBbbkkkkkkbbdk.",
                    ".kBdddddddddddk.",
                    ".kddddddddddddk.",
                    ".kkkkkkkkkkkkkk.",
                    "................"
            },
            // 46: PILLAR
            {
                    "................",
                    "..kkkkkkkkkkkk..",
                    ".kSSSSSSSSSSSSk.",
                    ".kssssssssssssk.",
                    "..kddddddddddk..",
                    "...kssSSssddk...",
                    "...kssSSssddk...",
                    "...kssSSssddk...",
                    "...kssSSssddk...",
                    "...kssSSssddk...",
                    "...kssSSssddk...",
                    "...kssSSssddk...",
                    "..kSSSSSSSSSSk..",
                    ".kssssssssssssk.",
                    ".kddddddddddddk.",
                    ".kkkkkkkkkkkkkk."
            },
            // 47: DOOR
            {
                    "................",
                    "..kkkkkkkkkkkk..",
                    ".kbbbbbbbbbbbbk.",
                    ".kbBBbbddbbBBbk.",
                    ".kssssssssssssk.",
                    ".kbBBbbddbbBBbk.",
                    ".kbBBbbddbbBBbk.",
                    ".kbBBbbddYYBBbk.",
                    ".kbBBbbddYYBBbk.",
                    ".kbBBbbddbbBBbk.",
                    ".kssssssssssssk.",
                    ".kbBBbbddbbBBbk.",
                    ".kbBBbbddbbBBbk.",
                    ".kddddddddddddk.",
                    ".kkkkkkkkkkkkkk.",
                    "................"
            },
            // 48: LOOT_BLADE
            {
                    "................",
                    "................",
                    "................",
                    "...........kk...",
                    "..........kSSk..",
                    ".........kSSsk..",
                    "........kSSsk...",
                    ".......kSSsk....",
                    "......kSSsk.....",
                    ".....kSSsk......",
                    "...kkssk........",
                    "..kYYkk.........",
                    "..kbk...........",
                    ".kbbk...........",
                    ".kkk............",
                    "................"
            },
            // 49: LOOT_OIL
            {
                    "................",
                    "......kkkk......",
                    "......kbbk......",
                    "......kbbk......",
                    ".....kiiiik.....",
                    ".....kiiiik.....",
                    "....kiOOOOik....",
                    "...kiOOOOOOik...",
                    "...kiOYYYYOik...",
                    "...kiOOOOOOik...",
                    "...kiOOOOOOik...",
                    "...kiooooooik...",
                    "....kiiiiiik....",
                    ".....kkkkkk.....",
                    "................",
                    "................"
            },
            // 50: LOOT_WATER
            {
                    "................",
                    "......kkkk......",
                    "......kbbk......",
                    "......kbbk......",
                    ".....kiiiik.....",
                    ".....kiiiik.....",
                    "....kiBBBBik....",
                    "...kiBBBBBBik...",
                    "...kiBwwwwBik...",
                    "...kiBBBBBBik...",
                    "...kiBBBBBBik...",
                    "...kibbbbbbik...",
                    "....kiiiiiik....",
                    ".....kkkkkk.....",
                    "................",
                    "................"
            },
            // 51: LOOT_ACID
            {
                    "................",
                    "......kkkk......",
                    "......kbbk......",
                    "......kbbk......",
                    ".....kiiiik.....",
                    ".....kiiiik.....",
                    "....kiAAAAik....",
                    "...kiAAAAAAik...",
                    "...kiALLLLAik...",
                    "...kiAAAAAAik...",
                    "...kiAAAAAAik...",
                    "...kiaaaaaaik...",
                    "....kiiiiiik....",
                    ".....kkkkkk.....",
                    "................",
                    "................"
            },
            // 52: LOOT_STONE
            {
                    "................",
                    "................",
                    "................",
                    "................",
                    ".....kkkkkk.....",
                    "....kSSSSSSk....",
                    "...kSSssssssk...",
                    "...kSssssdddk...",
                    "...kssssddddk...",
                    "....kssddddk....",
                    ".....kkkkkk.....",
                    "................",
                    "................",
                    "................",
                    "................",
                    "................"
            },
            // 53: BRAZIER_OFF
            {
                    "................",
                    "................",
                    "................",
                    "................",
                    "................",
                    "....kddddddk....",
                    "...kkkkkkkkkk...",
                    "..kssssssssssk..",
                    "..ksSSSSSSSSsk..",
                    "...kssssssssk...",
                    "....ksskkssk....",
                    "....ksk..ksk....",
                    "...kssk..kssk...",
                    "...ksk....ksk...",
                    "..kkkk....kkkk..",
                    "................"
            },
            // 54: THRALL_MOVE_0
            {
                    "................",
                    "......kkkk......",
                    ".....kTTTTk.....",
                    ".f..kTTttTTk....",
                    "fYf.kTkkkkTk....",
                    "fYffkTkCCkTk....",
                    "kWkkkTkkkkTk....",
                    "kwkk.kkttttk..s.",
                    "kwkkttTTTTtk.ss.",
                    "kdkktTTTTTTtksk.",
                    ".k.kttTTTTttkk..",
                    "....kttttttkk...",
                    "....kttk.kttk...",
                    "...kttk...kttk..",
                    "...kddk...kddk..",
                    "...kkkk...kkkk.."
            },
            // 55: THRALL_MOVE_1
            {
                    "......kkkk......",
                    ".....kTTTTk.....",
                    "....kTTttTTk....",
                    "..f.kTkkkkTk....",
                    "fYffkTkCCkTk....",
                    "fWYfkTkkkkTk....",
                    "kWkk.kkttttkk...",
                    "kwkkttTTTTttks..",
                    "kwkktTTTTTTtkss.",
                    "kdk.kttTTTTtksk.",
                    ".k..kttttttkk...",
                    "....kttttttkk...",
                    ".....kttkkttk...",
                    ".....kttk.kdk...",
                    ".....kddk.kkk...",
                    ".....kkkk......."
            },
            // 56: THRALL_MOVE_2
            {
                    "................",
                    "......kkkk......",
                    ".....kTTTTk.....",
                    ".f..kTTttTTk....",
                    "fYffkTkkkkTk....",
                    "fWffkTkCCkTk....",
                    "kWkkkTkkkkTk....",
                    "kwkk.kkttttkk...",
                    "kwkkttTTTTttks..",
                    "kdkktTTTTTTtkss.",
                    ".k..kttTTTTtksk.",
                    "....kttttttkk...",
                    "....kttk.kttk...",
                    "...kttk...kttk..",
                    "..kddk.....kddk.",
                    "..kkkk.....kkkk."
            },
            // 57: THRALL_MOVE_3
            {
                    "......kkkk......",
                    ".....kTTTTk.....",
                    "....kTTttTTk....",
                    ".f..kTkkkkTk....",
                    "fYf.kTkCCkTk....",
                    "fYYfkTkkkkTk....",
                    "kWkk.kkttttkk.s.",
                    "kwkkttTTTTttkss.",
                    "kwkktTTTTTTtksk.",
                    "kdk.kttTTTTtkk..",
                    ".k..kttttttkk...",
                    "....kttttttkk...",
                    "....kttkkttk....",
                    "....kdk..kttk...",
                    "....kkk..kddk...",
                    ".........kkkk..."
            },
            // 58: THRALL_ATTACK_0
            {
                    ".....kkkk.......",
                    "....kTTTTk...k..",
                    "...kTTttTTk.kSk.",
                    "...kTkkkkTkkSSk.",
                    "...kTkCCkTkSSk..",
                    ".f.kTkkkkTksSk..",
                    "fYfk.kkttttkk...",
                    "fYfkttTTTTtk....",
                    "kWkk.tTTTTttk...",
                    "kwk..ktttttk....",
                    "kdk..ktttttk....",
                    ".k...kttkkttk...",
                    "....kttk..kttk..",
                    "....kttk..kttk..",
                    "....kddk..kddk..",
                    "....kkkk..kkkk.."
            },
            // 59: THRALL_ATTACK_1
            {
                    "................",
                    ".......kkkk.....",
                    "......kTTTTk.ww.",
                    ".....kTTttTTkwSw",
                    ".....kTkkkkTk.Sw",
                    "..f..kTkCCkTk.SS",
                    ".fYf.kTkkkkTkSSs",
                    ".fYfk.kkttttkSS.",
                    ".kWkktTTTTttkss.",
                    ".kwkktTTTTTTtk..",
                    ".kdk.kttttttkk..",
                    "..k..kttkkttk...",
                    "....kttk..kttk..",
                    "....kttk..kttk..",
                    "....kddk..kddk..",
                    "....kkkk..kkkk.."
            },
            // 60: THRALL_ATTACK_2
            {
                    "................",
                    ".......kkkk.....",
                    "......kTTTTk....",
                    ".....kTTttTTk...",
                    ".....kTkkkkTk..s",
                    "..f..kTkCCkTk..S",
                    ".fYf.kTkkkkTk..s",
                    ".fYfkkttttkk....",
                    ".kWkkTTTTTTtk...",
                    ".kwkktTTTTTTkk..",
                    ".kdk.kttttttksS.",
                    "..k..kttkkttkkSS",
                    ".....kttk.kttk.w",
                    "....kttk...kttk.",
                    "....kddk...kddk.",
                    "....kkkk...kkkk."
            },
            // 61: THRALL_ATTACK_3
            {
                    "......kkkk......",
                    ".....kTTTTk.....",
                    "....kTTttTTk....",
                    "....kTkkkkTk....",
                    "....kTkCCkTk....",
                    ".f..kTkkkkTk....",
                    "fYf.kkttttkk....",
                    "fYfkttTTTTttk...",
                    "kWkktTTTTTTtk.s.",
                    "kwkkttTTTTttksS.",
                    "kdk.kttttttkksk.",
                    ".k..kttkkttk.k..",
                    "....kttk.kttk...",
                    "....kttk.kttk...",
                    "....kddk.kddk...",
                    "....kkkk.kkkk..."
            },
            // 62: THRALL_CAST_0
            {
                    "................",
                    "......kkkk......",
                    ".....kTTTTk..C..",
                    ".f..kTTttTTkCwC.",
                    "fYf.kTkkkkTkTCk.",
                    "fYffkTkCCkTktkk.",
                    "kWkkkTkkkkTktk..",
                    "kwkk.kkttttkk...",
                    "kwk.ttTTTTttk...",
                    "kdk.kttttttkk...",
                    ".k..kttttttkk...",
                    "....kttttttkk...",
                    "....kttkkttk....",
                    "...kttk..kttk...",
                    "...kddk..kddk...",
                    "...kkkk..kkkk..."
            },
            // 63: THRALL_CAST_1
            {
                    "...C..kkkk..C...",
                    "..CwCkTTTTkCwC..",
                    ".f.CkTTttTTkC...",
                    "fYf.kTkkkkTk.C..",
                    "fWffkTkCCkTkwCC.",
                    "kWkkkTkkkkTkCwC.",
                    "kwk.kkttttkkCT..",
                    "kwkttTTTTTTtk...",
                    "kdk.ktTTTTttk...",
                    ".k..ktttttttk...",
                    "...kttttttttkk..",
                    "...ktttkktttk...",
                    "....kttk.kttk...",
                    "....kddk.kddk...",
                    "....kkkk.kkkk...",
                    "..C..........C.."
            },
            // 64: THRALL_CAST_2
            {
                    ".......kkkk...C.",
                    "......kTTTTk.Cw.",
                    ".....kTTttTTkCwC",
                    ".f...kTkkkkTkwwC",
                    "fYf..kTkCCkTkwCC",
                    "fWff.kTkkkkTkCwC",
                    "kWkk.kkttttkkTC.",
                    "kwk.ttTTTTTTTtk.",
                    "kwkktTTTTTTTtk..",
                    "kdk.ktttttttkk..",
                    ".k.kttttttttk...",
                    "...ktttk.ktttk..",
                    "...kttk...kttk..",
                    "...kttk...kttk..",
                    "...kddk...kddk..",
                    "...kkkk...kkkk.."
            },
            // 65: THRALL_CAST_3
            {
                    "......kkkk......",
                    ".....kTTTTk.....",
                    "....kTTttTTk.C..",
                    "....kTkkkkTk..C.",
                    "....kTkCCkTk.Cw.",
                    ".f..kTkkkkTk..C.",
                    "fYf.kkttttkkT...",
                    "fYfkttTTTTttk...",
                    "kWkktTTTTTTtk.s.",
                    "kwkkttTTTTttks..",
                    "kwk.kttttttksk..",
                    "kdk.kttttttkk...",
                    ".k..kttkkttk....",
                    "....kttk.kttk...",
                    "....kddk.kddk...",
                    "....kkkk.kkkk..."
            },
            // 66: THRALL_GUARD
            {
                    "................",
                    "......kkkk......",
                    ".....kTTTTk.....",
                    "....kTTttTTk....",
                    ".f..kTkkkkTk.S..",
                    "fYf.kTkCCkTkkSk.",
                    "fYffkTkkkkTkkSk.",
                    "kWkk.kkttttkSSk.",
                    "kwkkttTTTTtkssk.",
                    "kwkktTTTTTTkYYk.",
                    "kdk.kttTTTTkdk..",
                    ".k..kttttttkk...",
                    "....kttkkttk....",
                    "...kttk..kttk...",
                    "...kddk..kddk...",
                    "...kkkk..kkkk..."
            },
            // 67: THRALL_HURT
            {
                    "................",
                    "....kkkk........",
                    "...kTTTTk.......",
                    "..kTTttTTk...r..",
                    "..kTkkkkTk..r...",
                    "..kTkrwkTk......",
                    ".f.kTkkkkTk.....",
                    "fYfk.kkttttk....",
                    "kWkkttTTTTttk...",
                    "kwk.ktTTTTTTks..",
                    "kdk.ktttttttkss.",
                    ".k..kttkkttkk...",
                    "...kttk..kttk...",
                    "...kttk...kttk..",
                    "...kddk...kddk..",
                    "...kkkk...kkkk.."
            }
    };

    public static final int GLOW_SIZE = 32;

    public final Texture texture, glowTex;
    public final TextureRegion[] regions = new TextureRegion[COUNT];
    public final TextureRegion white, glow;

    public PixelAtlas() {
        int[] pixels = bakeRgbaPixels();
        Pixmap pm = new Pixmap(ATLAS_W, ATLAS_H, Pixmap.Format.RGBA8888);
        for (int y = 0; y < ATLAS_H; y++) {
            for (int x = 0; x < ATLAS_W; x++) {
                pm.drawPixel(x, y, pixels[y * ATLAS_W + x]);
            }
        }
        texture = new Texture(pm);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose();

        for (int i = 0; i < COUNT; i++) {
            int cx = (i % COLS) * CELL;
            int cy = (i / COLS) * CELL;
            regions[i] = new TextureRegion(texture, cx, cy, CELL, CELL);
        }
        int wx = (COUNT % COLS) * CELL + 4;
        int wy = (COUNT / COLS) * CELL + 4;
        white = new TextureRegion(texture, wx, wy, 4, 4);

        int[] glowPixels = bakeGlowPixels();
        Pixmap gp = new Pixmap(GLOW_SIZE, GLOW_SIZE, Pixmap.Format.RGBA8888);
        for (int y = 0; y < GLOW_SIZE; y++) {
            for (int x = 0; x < GLOW_SIZE; x++) {
                gp.drawPixel(x, y, glowPixels[y * GLOW_SIZE + x]);
            }
        }
        glowTex = new Texture(gp);
        glowTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        gp.dispose();
        glow = new TextureRegion(glowTex);
    }

    public void dispose() {
        texture.dispose();
        glowTex.dispose();
    }

    /** Bakes a smooth 32x32 radial gradient for Linear-filtered shadows and additive light halos. */
    public static int[] bakeGlowPixels() {
        int[] out = new int[GLOW_SIZE * GLOW_SIZE];
        float mid = (GLOW_SIZE - 1) * 0.5f;
        for (int y = 0; y < GLOW_SIZE; y++) {
            for (int x = 0; x < GLOW_SIZE; x++) {
                float dx = (x - mid) / mid, dy = (y - mid) / mid;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d >= 1f) continue;
                float f = 1f - d;
                int a = Math.min(255, Math.round(255f * f * f));
                out[y * GLOW_SIZE + x] = 0xFFFFFF00 | a;
            }
        }
        return out;
    }

    /** Bakes all 68 16x16 sprites + cell 68 solid white block into RGBA8888 pixels. */
    public static int[] bakeRgbaPixels() {
        int[] out = new int[ATLAS_W * ATLAS_H];
        for (int idx = 0; idx < COUNT; idx++) {
            String[] rows = SPRITES[idx];
            int ox = (idx % COLS) * CELL;
            int oy = (idx / COLS) * CELL;
            for (int y = 0; y < CELL; y++) {
                String row = rows[y];
                for (int x = 0; x < CELL; x++) {
                    char ch = row.charAt(x);
                    out[(oy + y) * ATLAS_W + (ox + x)] = paletteColor(idx, ch);
                }
            }
        }
        int wx = (COUNT % COLS) * CELL;
        int wy = (COUNT / COLS) * CELL;
        for (int y = 0; y < CELL; y++) {
            for (int x = 0; x < CELL; x++) {
                out[(wy + y) * ATLAS_W + (wx + x)] = 0xFFFFFFFF;
            }
        }
        return out;
    }

    public static int spriteForEntity(Scene s, int i, int animFrame) {
        int frame = s.frozenEntity[i] ? 0 : animFrame;
        if (i == 0) return THRALL_0 + frame;
        if (s.kind[i] == Entity.Kind.DOOR) return DOOR;
        if (s.kind[i] == Entity.Kind.PROP) {
            return switch (s.glyph[i]) {
                case 'i' -> s.litEntity[i] ? BRAZIER_0 + frame : BRAZIER_OFF;
                case 'B' -> BARREL;
                case 'c' -> CRATE;
                case 'S' -> PILLAR;
                default -> CRATE;
            };
        }
        return switch (s.glyph[i]) {
            case 'O' -> ORC_0 + frame;
            case 'G' -> s.maxHp[i] >= 35 ? OGRE_0 + frame : GOBLIN_0 + frame;
            case 'g' -> GOBLIN_0 + frame;
            case 'r' -> RAT_0 + frame;
            default -> ORC_0 + frame;
        };
    }

    public static int thrallSprite(int pose, int frame) {
        return switch (pose) {
            case POSE_MOVE -> THRALL_MOVE_0 + frame;
            case POSE_ATTACK -> THRALL_ATTACK_0 + frame;
            case POSE_CAST -> THRALL_CAST_0 + frame;
            case POSE_GUARD -> THRALL_GUARD;
            case POSE_HURT -> THRALL_HURT;
            default -> THRALL_0 + frame;
        };
    }

    public static int spriteForLoot(byte lootKind) {
        return switch (lootKind) {
            case Scene.LOOT_BLADE -> LOOT_BLADE;
            case Scene.LOOT_FLASK_OIL -> LOOT_OIL;
            case Scene.LOOT_FLASK_WATER -> LOOT_WATER;
            case Scene.LOOT_FLASK_ACID -> LOOT_ACID;
            case Scene.LOOT_STONE -> LOOT_STONE;
            default -> LOOT_STONE;
        };
    }

    private static int paletteColor(int idx, char ch) {
        if (ch == '.') return 0x00000000;
        if (ch == 'k') return 0x0B0E14FF;
        // Floor / Wall stone shades
        if (idx <= WALL_FRONT || idx == PILLAR || idx == LOOT_STONE) {
            return switch (ch) {
                case 'c' -> 0x1D2532FF;
                case 'C' -> 0x263142FF;
                case 'd' -> 0x141A24FF;
                case 's' -> 0x323E52FF;
                case 'S' -> 0x4A5872FF;
                default -> 0x202836FF;
            };
        }
        // Water / Ice / Acid / Oil / Tar / Fire / Gas
        if (idx >= WATER_0 && idx <= WATER_3) {
            return switch (ch) {
                case 'b' -> 0x1B4D7ACC;
                case 'B' -> 0x2B72AACC;
                case 'w' -> 0x9AD8F5EE;
                default -> 0x1B4D7ACC;
            };
        }
        if (idx >= ACID_0 && idx <= ACID_3) {
            return switch (ch) {
                case 'a' -> 0x4A9228DD;
                case 'A' -> 0x73C73CEE;
                case 'L' -> 0xB8F550FF;
                default -> 0x4A9228DD;
            };
        }
        if (idx == OIL_SLICK || idx == TAR_TILE) {
            return switch (ch) {
                case 'o' -> 0x1F1812E0;
                case 'O' -> 0x463623EE;
                case 'Y' -> 0x8F6836FF;
                case 'a' -> 0x3D6328DD;
                case 'L' -> 0x84CC32EE;
                default -> 0x1F1812E0;
            };
        }
        if (idx == ICE_TILE) {
            return switch (ch) {
                case 'i' -> 0x6BBBD8CC;
                case 'I' -> 0x9CE0F5DD;
                case 'w' -> 0xE6F9FFFF;
                default -> 0x6BBBD8CC;
            };
        }
        if (idx >= FIRE_0 && idx <= FIRE_3) {
            return switch (ch) {
                case 'f' -> 0xD94E28E6;
                case 'Y' -> 0xF59E2BFF;
                case 'W' -> 0xFFF3A8FF;
                default -> 0x00000000;
            };
        }
        if (idx == GAS_PUFF) {
            return switch (ch) {
                case 'w' -> 0xFFFFFF77;
                case 'W' -> 0xFFFFFFCC;
                default -> 0x00000000;
            };
        }
        // Creatures, props, loot
        return switch (ch) {
            case 'T' -> 0x4CC9B0FF; // Thrall bright teal
            case 't' -> 0x236862FF; // Thrall dark cloak
            case 'C' -> 0x90FFF0FF; // Thrall cyan eyes
            case 'g' -> 0x66A84DFF; // Orc green
            case 'G' -> 0x77BF49FF; // Goblin green
            case 'o' -> 0x8F5E3DFF; // Ogre hide
            case 'r' -> 0xC43B3BFF; // Red eyes / anger
            case 'p' -> 0xC97C8CFF; // Rat pink ear/tail
            case 's' -> 0x6C7A8FFF; // Steel mid
            case 'S' -> 0xB8C7D9FF; // Steel bright
            case 'd' -> 0x3F2B1DFF; // Dark wood / leather
            case 'b' -> 0x7C4F2EFF; // Wood mid
            case 'B' -> 0xB07645FF; // Wood highlight
            case 'O' -> 0xD9822BFF; // Oil amber
            case 'A' -> 0x84E03BFF; // Acid liquid
            case 'L' -> 0xD1FF7AFF; // Acid highlight
            case 'i' -> 0xA8C5D9CC; // Glass
            case 'f' -> 0xE85826FF; // Flame orange
            case 'Y' -> 0xF5C242FF; // Gold / flame yellow
            case 'W' -> 0xFFF4B8FF; // White-hot flame
            case 'w' -> 0xE8EEF5FF; // White
            default -> 0xFFFFFFFF;
        };
    }
}
