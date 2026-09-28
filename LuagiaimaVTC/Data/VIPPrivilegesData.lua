VIPPrivilegesData = {};
VIPPrivilegesData.__index = VIPPrivilegesData;

function VIPPrivilegesData.New(reader)
  local self = {};
  setmetatable(self, VIPPrivilegesData);

  self.lv = reader:ReadByte();
  self.exp = reader:ReadInt32();
  self.dungeon = reader:ReadByte();
  self.card = reader:ReadByte();
  self.mount = reader:ReadByte();
  self.epicDungeon = reader:ReadByte();

  self.expTurn0 = reader:ReadUInt16();
  self.expTurn1 = reader:ReadUInt16();
  self.expTurn2 = reader:ReadUInt16();
  self.expTurn3 = reader:ReadUInt16();

  return self.lv, self;
end
