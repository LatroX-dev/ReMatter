"""Work animations keep real hardware and palette identity, with continuous loop endpoints."""
from pathlib import Path
import json
import math
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import renderer_parts
import generate_family_assets as family
import generate_module_assets as modules
import generate_energy_assets as energy
import work_sprites
from family_sprites import sprites,ANIMATIONS


class RendererHardwareTests(unittest.TestCase):
    def test_rest_and_stroke_ends_fit_baked_hardware_and_the_generated_source_is_current(self):
        self.assertEqual(renderer_parts.validate(),20)
        self.assertEqual(renderer_parts.JAVA.read_text(),renderer_parts.java_source())

    def test_world_has_moving_hardware_once_and_item_keeps_its_static_showpiece(self):
        f=family.family()
        for device in (*family.BENCHES,'chemical_reactor'):
            for state in ('idle','active'):
                world=f.models[device+'_'+state+'_world_parts']['elements']
                self.assertFalse(any(p['name']=='status_light' for p in world))
            item=f.models[device+'_active_parts']['elements']
            self.assertTrue(any(p['name']=='status_light' for p in item))
        for name in ('grinding_unit','mixing_basin'):
            module=modules.Module(name,modules.mount_sheet())
            for suffix in ('','_suspended'):
                baked=module.models[name+suffix+'_world']['elements']
                work=module.models[name+suffix+'_active_world_parts']['elements']
                self.assertFalse(any(p['name'] in ('blade','paddle','cutting_edge') for p in baked+work))
            inventory=module.models[name]['elements']+module.models[name+'_active_parts']['elements']
            expected={'grinding_unit':{'blade','cutting_edge'},'mixing_basin':{'paddle'}}[name]
            self.assertTrue(expected.issubset({p['name'] for p in inventory}))

    def test_rotating_paddle_stays_inside_basin_through_a_whole_turn(self):
        lo,hi=renderer_parts.PARTS['MODULE_PADDLE']
        radius=max(math.hypot(x-8,z-8) for x in (lo[0],hi[0]) for z in (lo[2],hi[2]))
        self.assertLess(radius,5)
        self.assertEqual(lo[1],6);self.assertEqual(hi[1],7)


class WorkStripTests(unittest.TestCase):
    def test_family_glows_keep_opacity_and_have_smooth_sixteen_frame_metadata(self):
        art=sprites()
        for name,meta in ANIMATIONS.items():
            if name=='led_red' or 'primal' in name:continue
            with self.subTest(sprite=name):
                a=art[name];self.assertGreaterEqual(a.height//a.width,16)
                self.assertIs(meta['animation'].get('interpolate'),True)
                footprint=[a.pixel(x,y)[3] for y in range(a.width) for x in range(a.width)]
                for f in range(a.height//a.width):
                    self.assertEqual(footprint,[a.pixel(x,y+f*a.width)[3] for y in range(a.width) for x in range(a.width)])

    def test_every_cube_charge_has_both_flow_directions_and_none_uses_static_art(self):
        f,art=energy.assets()
        for name in energy.TIERS:
            selectors=[p['when'] for p in f.blockstates[name]['multipart'] if 'charge' in p.get('when',{})]
            self.assertEqual({(p['charge'],p['flow']) for p in selectors},{(str(c),flow) for c in range(9) for flow in ('none','in','out')})
        for charge in range(1,9):
            self.assertEqual(art[f'energy_core_charge_{charge}'].height,32)
            for flow in ('in','out'):self.assertEqual(art[f'energy_core_charge_{charge}_{flow}'].height,512)
            # Reverse time reverses the band, while every texel of the casing stays put.
            for frame in range(16):
                incoming=work_sprites.energy_core(charge,frame,1)
                outgoing=work_sprites.energy_core(charge,(-frame)%16,-1)
                self.assertEqual(incoming.rows,outgoing.rows)


if __name__=='__main__':unittest.main()
