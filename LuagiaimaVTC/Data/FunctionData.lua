
FunctionData = {}
FunctionData.__index = FunctionData;

function FunctionData.New(reader)
  local self = {};
  setmetatable(self, FunctionData);

  local id = reader:ReadByte();
  
  self.kind = reader:ReadByte();
  self.group = reader:ReadByte(); --1:右上 2:左上 3:右下 5:戰鬥橫 6:戰鬥直

  self.name = reader:ReadUInt32();
  self.icon = LuaHelper.BytesToString(DataManager.encoding_Unicode, reader:ReadBytes(reader:ReadUInt16()));
  self.aniBtn = LuaHelper.BytesToString(DataManager.encoding_Unicode, reader:ReadBytes(reader:ReadUInt16()));

  self.showLv = reader:ReadByte();
  self.showFlag = reader:ReadUInt16();

  self.requireLv = reader:ReadByte();
  self.requireFlag = reader:ReadUInt16();
  self.requireMsg = reader:ReadUInt32();

  self.serverSwitch = reader:ReadByte();
  
  return id, self;
end