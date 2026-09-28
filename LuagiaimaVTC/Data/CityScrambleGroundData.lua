CityScrambleGroundData = {}
CityScrambleGroundData.__index = CityScrambleGroundData;

function CityScrambleGroundData.New(reader)
  local self = {};
  setmetatable(self, CityScrambleGroundData);

  self.groundID = reader:ReadUInt16(); --城鎮ID
  self.icon = LuaHelper.BytesToString(DataManager.encoding_Unicode, reader:ReadBytes(reader:ReadUInt16())); --icon名稱

  return self.groundID, self;
end