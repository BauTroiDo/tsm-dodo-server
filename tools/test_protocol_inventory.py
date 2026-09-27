import unittest
from protocol_inventory import args_at, mask, scan


class InventoryTest(unittest.TestCase):
    def test_comments_strings_and_long_brackets_do_not_create_calls(self):
        source = '''-- Network.Send(1, 2)
--[=[ Network.Send(3,4) ]=]
local a = "Network.Send(5,6)"
local b = [==[ Network.Send(7,8) ]==]
Network.Send(9, 1, f(1, 2))
'''
        code = mask(source)
        self.assertEqual(len(source), len(code))
        self.assertEqual(source.count('\n'), code.count('\n'))
        self.assertEqual(1, code.count('Network.Send'))
        start = code.index('Network.Send(') + len('Network.Send(')
        self.assertEqual(['9', '1', 'f(1, 2)'], args_at(code, start))

    def test_default_sub_nested_calls_and_nil(self):
        self.assertEqual(['3'], args_at('3)', 0))
        self.assertEqual(['1', 'nil', 'f({1,2}, a[2])'], args_at('1, nil, f({1,2}, a[2]))', 0))

    def test_repository_coverage_regressions(self):
        data = scan()
        rows = {(r['direction'], r['main'], r['sub']): r for r in data['rows']}
        self.assertEqual(792, data['lua_files'])
        for key in [('C2S',1,0), ('C2S',10,0), ('C2S',3,0), ('C2S',50,1),
                    ('C2S',50,2), ('C2S',20,8), ('C2S',30,1), ('S2C',65,14),
                    ('S2C',0,0), ('S2C',20,1)]:
            self.assertIn(key, rows)
            self.assertTrue(rows[key]['kotlin'], key)
        self.assertEqual(['lua/UI/UIDebug.lua:462'], [x['source'] for x in data['unresolved_calls']])
        self.assertEqual('MISSING', rows['C2S',107,2]['status'])
        self.assertEqual('MISSING', rows['C2S',28,2]['status'])
        self.assertEqual(107, data['opcodes']['ELF'])
        self.assertEqual(0, data['opcodes']['DISCONNECT'])
        self.assertEqual('EventManager.ReceiveCommonEvent', rows['S2C',20,1]['lua'][0]['callback'])
        self.assertNotIn('RECONNECT', data['opcodes'])


if __name__ == '__main__':
    unittest.main()
