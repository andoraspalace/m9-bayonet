import argparse
from typing import NamedTuple

from cs2assets import reflectivity, sounds
from cs2assets.clips import ClipSet, build_clips, clip_sound_names
from cs2assets.images import NATIVE_SIZE
from cs2assets.materials import Material
from cs2assets.models import build, build_model
from cs2assets.paths import MODELS, find
from cs2assets.reflectivity import AlphaRange
from cs2assets.skinning import skinned_mesh
from cs2assets.textures import TEXTURE_SIZE, bake_anodized, bake_color, bake_custom_paint, bake_glove, bake_with_ao, build_legacy_wear_map, build_shared_wear_map

AWP_MODEL = "weapons/models/awp/weapon_snip_awp.glb"
AWP_MATERIAL = "awp"
AWP_HD_NODE = "body_hd"
AWP_COLOR = "materials/models/weapons/v_models/snip_awp/awp_substrate_color_psd_*.png"
AWP_AO = "materials/models/weapons/v_models/snip_awp/awp_ao_psd_*.png"
ASIMOV_ALPHA = AlphaRange(16 / 255, 58 / 255)
SCOPE_MATERIAL = "shared_scope"
SCOPE_TEXTURE = "weapons/models/awp/shared_scope_vmat_g_tcolor_*.png"
SCOPE_LENS_F0 = (0.08, 0.1, 0.16)
SCOPE_LENS_ROUGHNESS = 0.0

PAINT_KIT_VMAT = "materials/**/paints/vmats/{}.vmat.data.txt"
COMPOSITE_MASKS = "masks_psd_*.png"
COMPOSITE_AO = "ao_psd_*.png"
COMPOSITE_ROUGHNESS = "rough.png"

PAINT_WEAR = "materials/**/shared/paint_wear_psd_*.png"
GUN_GRUNGE = "materials/**/shared/gun_grunge_psd_*.png"
LEGACY_WEAR_MAP = "awp_legacy"
SHARED_WEAR_MAP = "shared"
LEGACY_WEAR = {"wear_maps": LEGACY_WEAR_MAP}

GLOVE_MODEL = "agents/models/shared/arms/glove_sporty/glove_sporty.glb"
GLOVE_WORLD_NODE = "worldmodel"
GLOVE_VMAT = "gloves/paints/sporty_blue_pink.vmat.data.txt"
GLOVE_LEFT_MATERIAL = "glove_sporty_left"
GLOVE_RIGHT_MATERIAL = "glove_sporty_right"
BARE_ARM_MATERIAL = "bare_arm_133"
BARE_ARM_COLOR = "characters/models/shared/arms/bare_arms/materials/bare_arm_133_color_psd_*.png"
BARE_ARM_VMAT = "characters/models/shared/arms/bare_arms/materials/bare_arm_133.vmat.data.txt"

AGENT_MODEL = "agents/models/tm_phoenix/tm_phoenix.glb"
AGENT_DUMP = "agents/models/tm_phoenix/tm_phoenix.vmdl.data.txt"
SLEEVES_MESH = "firstperson_sleeves"
JACKET_MATERIAL = "tm_phoenix_v2_body_variante"
JACKET_COLOR = "characters/models/tm_phoenix/materials/tm_phoenix_v2_body_variante_color_psd_*.png"
JACKET_AO = "characters/models/tm_phoenix/materials/tm_phoenix_v2_body_ao_psd_*.png"
JACKET_SIZE = TEXTURE_SIZE // 2
JACKET_ROUGHNESS = 0.8

STATTRAK_MODEL = "weapons/models/shared/stattrak/stattrak_module.glb"
STATTRAK_MATERIAL = "stattrak_module"
STATTRAK_COLOR = "weapons/models/shared/stattrak/materials/stattrak_module_color_tga_*.png"
STATTRAK_AO = "weapons/models/shared/stattrak/materials/stattrak_module_ao_tga_*.png"
STATTRAK_DISPLAY_MATERIAL = "stattrak_module_display"
STATTRAK_DIGITS = "weapons/models/shared/stattrak/materials/stattrak_digit_atlas_psd_*.png"

AWP_SHOT = "Weapon_AWP.Single"
GAME_SOUNDS = (
    AWP_SHOT,
    "Weapon_Knife.HitWall",
    "Weapon_Knife.Hit.Light.Flesh",
    "Weapon_Knife.Hit.Heavy.Flesh",
    "Weapon_Knife.Hit.Light.Backstab.Flesh",
    "Weapon_Knife.Hit.Heavy.Backstab.Flesh",
)
LOUDNESS_ANCHOR = AWP_SHOT

CLIP_SETS = (
    ClipSet("rifle/rifle_awp", "_awp", "awp_anims"),
    ClipSet("knife/knife_m9", "_m9", "m9_anims"),
    ClipSet("knife/knife_butterfly", "_butterfly", "butterfly_anims"),
)


class AwpSkin(NamedTuple):
    target: str
    paint_glob: str
    alpha_range: AlphaRange | None


class Knife(NamedTuple):
    model: str
    material: str
    color: str
    composite_prefix: str
    default_ao: str | None

    @property
    def rough_glob(self):
        return self.composite_prefix + COMPOSITE_ROUGHNESS

    def bake(self, paint_kit):
        color = find(self.color)
        if paint_kit is not None:
            vmat = Material.read(find(PAINT_KIT_VMAT.format(paint_kit)))
            return bake_anodized(vmat, color, find(self.composite_prefix + COMPOSITE_MASKS), find(self.composite_prefix + COMPOSITE_AO))
        if self.default_ao is not None:
            return bake_with_ao(color, find(self.default_ao))
        return bake_color(color)


class KnifeSkin(NamedTuple):
    knife: Knife
    target: str
    paint_kit: str | None


AWP_SKINS = (
    AwpSkin("awp_asimov", "materials/**/paints/custom/workshop/zone9_awp_tga_*.png", ASIMOV_ALPHA),
    AwpSkin("awp_dragon_lore", "materials/models/weapons/customization/paints/custom/dragon_awp_psd_*.png", None),
)

M9 = Knife(
    "weapons/models/knife/knife_m9/weapon_knife_m9.glb",
    "weapon_knife_m9",
    "materials/models/weapons/v_models/knife_m9_bay/knife_m9_bay_color_psd_*.png",
    "weapons/models/knife/knife_m9/materials/composite_inputs/knife_m9_bay_",
    None,
)
BUTTERFLY = Knife(
    "weapons/models/knife/knife_butterfly/weapon_knife_butterfly.glb",
    "weapon_knife_butterfly",
    "materials/models/weapons/v_models/knife_butterfly/knife_butterfly_color_psd_*.png",
    "weapons/models/knife/knife_butterfly/materials/composite_inputs/knife_butterfly_",
    "materials/models/weapons/v_models/knife_butterfly/knife_butterfly_ao_psd_*.png",
)
KNIFE_SKINS = (
    KnifeSkin(M9, "m9_emerald", "am_emerald_marbleized"),
    KnifeSkin(M9, "m9_ruby", "am_ruby_marbleized"),
    KnifeSkin(M9, "m9_default", None),
    KnifeSkin(BUTTERFLY, "butterfly_default", None),
    KnifeSkin(BUTTERFLY, "butterfly_sapphire", "am_sapphire_marbleized_b"),
    KnifeSkin(BUTTERFLY, "butterfly_ruby", "am_ruby_marbleized"),
    KnifeSkin(BUTTERFLY, "butterfly_gamma_doppler", "am_gamma_doppler_phase2"),
)


def build_awps():
    model = find(AWP_MODEL)
    occlusion = find(AWP_AO)
    scope = find(SCOPE_TEXTURE)
    lens = reflectivity.uniform(SCOPE_LENS_F0, SCOPE_LENS_ROUGHNESS)
    for skin in AWP_SKINS:
        paint_texture = find(skin.paint_glob)
        build(
            model, MODELS / f"{skin.target}.glb",
            drop_nodes=(AWP_HD_NODE,),
            textures={AWP_MATERIAL: bake_custom_paint(paint_texture, occlusion), SCOPE_MATERIAL: scope},
            reflectivity={AWP_MATERIAL: reflectivity.custom_paint(paint_texture, skin.alpha_range), SCOPE_MATERIAL: lens},
            extras={AWP_MATERIAL: LEGACY_WEAR},
        )


def build_knives():
    for skin in KNIFE_SKINS:
        knife = skin.knife
        baked = knife.bake(skin.paint_kit)
        build(
            find(knife.model), MODELS / f"{skin.target}.glb",
            textures={knife.material: baked},
            reflectivity={knife.material: reflectivity.knife(baked, find(knife.rough_glob))},
        )


def build_arms():
    glove = Material.read(find(GLOVE_VMAT))
    glove_color = bake_glove(glove)
    glove_shine = reflectivity.glove(glove)
    bare_arm = find(BARE_ARM_COLOR)
    skin_shine = reflectivity.skin(Material.read(find(BARE_ARM_VMAT)))
    build(
        find(GLOVE_MODEL), MODELS / "arms.glb",
        drop_nodes=(GLOVE_WORLD_NODE,),
        textures={GLOVE_LEFT_MATERIAL: glove_color, GLOVE_RIGHT_MATERIAL: glove_color, BARE_ARM_MATERIAL: bare_arm},
        reflectivity={GLOVE_LEFT_MATERIAL: glove_shine, GLOVE_RIGHT_MATERIAL: glove_shine, BARE_ARM_MATERIAL: skin_shine},
    )
    agent = find(AGENT_MODEL)
    build_model(
        *skinned_mesh(agent, find(AGENT_DUMP), SLEEVES_MESH, (BARE_ARM_MATERIAL, JACKET_MATERIAL)),
        f"{SLEEVES_MESH} in {agent}",
        MODELS / "sleeves.glb",
        textures={BARE_ARM_MATERIAL: bare_arm, JACKET_MATERIAL: bake_with_ao(find(JACKET_COLOR), find(JACKET_AO), size=JACKET_SIZE)},
        reflectivity={BARE_ARM_MATERIAL: skin_shine, JACKET_MATERIAL: reflectivity.dielectric(JACKET_ROUGHNESS)},
    )


def build_stattrak():
    build(
        find(STATTRAK_MODEL), MODELS / "stattrak_module.glb",
        textures={
            STATTRAK_MATERIAL: bake_with_ao(find(STATTRAK_COLOR), find(STATTRAK_AO), size=NATIVE_SIZE),
            STATTRAK_DISPLAY_MATERIAL: find(STATTRAK_DIGITS),
        },
    )


def build_wear_maps():
    build_legacy_wear_map(LEGACY_WEAR_MAP, find(AWP_COLOR), find(AWP_AO))
    build_shared_wear_map(SHARED_WEAR_MAP, find(PAINT_WEAR), find(GUN_GRUNGE))


def played_sounds():
    return set(GAME_SOUNDS) | clip_sound_names(CLIP_SETS)


def parse_args():
    parser = argparse.ArgumentParser(description="Bake the CS2 export in cs2-export into the mod's models, wear maps and sounds.")
    parser.add_argument("--list-sounds", action="store_true", help="print the sound files to export, then stop")
    parser.add_argument("--allow-missing", action="store_true", help="build even if some needed sounds were not exported")
    return parser.parse_args()


def main():
    args = parse_args()
    if args.list_sounds:
        sounds.list_sounds(played_sounds())
        return

    events = sounds.collect_events(played_sounds(), args.allow_missing)
    MODELS.mkdir(parents=True, exist_ok=True)
    build_awps()
    build_knives()
    build_arms()
    build_stattrak()
    build_wear_maps()
    for clip_set in CLIP_SETS:
        build_clips(clip_set)
    sounds.build_sounds(events, LOUDNESS_ANCHOR)


if __name__ == "__main__":
    main()
