RandomNameData = {};
RandomNameData.__index = RandomNameData;

function RandomNameData.New(reader)
  local self = {};
  setmetatable(self, RandomNameData);
  
  local id = reader:ReadUInt16();    
  self.firstName = LuaHelper.BytesToString(DataManager.encoding_Unicode, reader:ReadBytes(reader:ReadUInt16()));
  self.boyName = LuaHelper.BytesToString(DataManager.encoding_Unicode, reader:ReadBytes(reader:ReadUInt16()));
  self.girlName = LuaHelper.BytesToString(DataManager.encoding_Unicode, reader:ReadBytes(reader:ReadUInt16()));
  self.spNameA = LuaHelper.BytesToString(DataManager.encoding_Unicode, reader:ReadBytes(reader:ReadUInt16()));
  self.spNameB = LuaHelper.BytesToString(DataManager.encoding_Unicode, reader:ReadBytes(reader:ReadUInt16()));

  return id, self;
end