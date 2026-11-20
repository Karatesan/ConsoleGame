package com.archon.ui;

import com.archon.model.Entity;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * Procedural 16x16 roguelike sprite atlas baked in memory at startup.
 * ATLAS_W x ATLAS_H = 256 x 64 (16x4 cells). Cell 36 is a solid white pixel block for Batch FX.
 */
public final class PixelAtlas {
    public static final int CELL = 16, COLS = 16, ROWS = 4;
    public static final int ATLAS_W = COLS * CELL, ATLAS_H = ROWS * CELL;

    public static final int
            FLOOR_0 = 0, FLOOR_1 = 1, WALL_TOP = 2, WALL_FRONT = 3,
            WATER_0 = 4, WATER_1 = 5, ACID_0 = 6, ACID_1 = 7,
            OIL_SLICK = 8, ICE_TILE = 9, TAR_TILE = 10, FIRE_0 = 11, FIRE_1 = 12, GAS_PUFF = 13,
            THRALL_0 = 14, THRALL_1 = 15, ORC_0 = 16, ORC_1 = 17,
            GOBLIN_0 = 18, GOBLIN_1 = 19, OGRE_0 = 20, OGRE_1 = 21, RAT_0 = 22, RAT_1 = 23,
            BRAZIER_0 = 24, BRAZIER_1 = 25, BARREL = 26, CRATE = 27, PILLAR = 28, DOOR = 29,
            LOOT_BLADE = 30, LOOT_OIL = 31, LOOT_WATER = 32, LOOT_ACID = 33, LOOT_STONE = 34,
            BRAZIER_OFF = 35,
            COUNT = 36;

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
                    ".bbbbbbbbwbbbbb.",
                    ".bbBbbbbwwwbbbb.",
                    ".bbbbbbbbbbbbbb.",
                    ".bbbwwbbbbbbbbb.",
                    ".bbwwwwbbbbBbbb.",
                    ".bbbbbbbbbbbbbb.",
                    ".bbbbbbbbbwwbbb.",
                    ".bbbbBbbbwwwwbb.",
                    ".bbbbBbbbbbbbbb.",
                    ".bbwbbbbbbbbbbb.",
                    ".bwwwbbbbbbbbbb.",
                    ".bbbbbbbbbbbbbb.",
                    "..bbbbbbbbbbbb..",
                    "................"
            },
            // 6: ACID_0
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
            // 7: ACID_1
            {
                    "................",
                    "..aaaaaaaaaaaa..",
                    ".aaaaaaaaLLaaaa.",
                    ".aaaAaaaLLLLaaa.",
                    ".aaaaaaaaLLaaaa.",
                    ".aaLLaaaaaaaaaa.",
                    ".aLLLLaaaaAaaaa.",
                    ".aaLLaaaaLLaaaa.",
                    ".aaaaaaaLLLLaaa.",
                    ".aaaaaaaaLLaaaa.",
                    ".aaaaLLaaaaaaaa.",
                    ".aaaLLLLaaaLLaa.",
                    ".aaaaLLaaaLLLLa.",
                    ".aaaaaaaaaaLLaa.",
                    "..aaaaaaaaaaaa..",
                    "................"
            },
            // 8: OIL_SLICK
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
            // 9: ICE_TILE
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
            // 10: TAR_TILE
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
            // 11: FIRE_0
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
            // 12: FIRE_1
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
            // 13: GAS_PUFF
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
            // 14: THRALL_0
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
            // 15: THRALL_1
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
                    "kdkktTTTTTTtks..",
                    ".k.kttTTTTttkk..",
                    "....kttttttkk...",
                    "....kttkkttk....",
                    "....kttk.kttk...",
                    "....kddk.kddk...",
                    "....kkkk.kkkk..."
            },
            // 16: ORC_0
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
            // 17: ORC_1
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
            // 18: GOBLIN_0
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
            // 19: GOBLIN_1
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
            // 20: OGRE_0
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
            // 21: OGRE_1
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
                    ".kk.kddkkddkkbbb",
                    "....kddkkddk.kbb",
                    "....kddkkddk....",
                    "....kkkkkkkk....",
                    "................"
            },
            // 22: RAT_0
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
            // 23: RAT_1
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
                    "...ksssssssssssk",
                    "....ksskssskkkpk",
                    ".....kk..kk..p..",
                    "................"
            },
            // 24: BRAZIER_0
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
            // 25: BRAZIER_1
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
            // 26: BARREL
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
            // 27: CRATE
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
            // 28: PILLAR
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
            // 29: DOOR
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
            // 30: LOOT_BLADE
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
            // 31: LOOT_OIL
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
            // 32: LOOT_WATER
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
            // 33: LOOT_ACID
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
            // 34: LOOT_STONE
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
            // 35: BRAZIER_OFF
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
            }
    };

    public final Texture texture;
    public final TextureRegion[] regions = new TextureRegion[COUNT];
    public final TextureRegion white;

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
    }

    public void dispose() {
        texture.dispose();
    }

    /** Bakes all 36 16x16 sprites + cell 36 solid white block into RGBA8888 pixels. */
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
        if (idx == WATER_0 || idx == WATER_1) {
            return switch (ch) {
                case 'b' -> 0x1B4D7ACC;
                case 'B' -> 0x2B72AACC;
                case 'w' -> 0x9AD8F5EE;
                default -> 0x1B4D7ACC;
            };
        }
        if (idx == ACID_0 || idx == ACID_1) {
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
        if (idx == FIRE_0 || idx == FIRE_1) {
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
