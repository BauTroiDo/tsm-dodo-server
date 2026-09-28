CityScrambleCityData = {}
CityScrambleCityData.__index = CityScrambleCityData;

function CityScrambleCityData.New(reader)
  local self = {};
  setmetatable(self, CityScrambleCityData);

  self.cityID = reader:ReadUInt16(); --城鎮ID
  self.cityType = reader:ReadByte(); --城鎮種類  
  self.cityName = LuaHelper.BytesToString(DataManager.encoding_Unicode, reader:ReadBytes(reader:ReadUInt16()));     --城鎮名稱
  self.spriteType = reader:ReadByte();

  return self.cityID, self;
end