AnotherWroldLvData = {};
AnotherWroldLvData.__index = AnotherWroldLvData;

function AnotherWroldLvData.New(reader)
  local self = {};
  setmetatable(self, AnotherWroldLvData);

  self.id = reader:ReadUInt16(); --流水號
  self.level = reader:ReadByte(); --地雷怪等級
  self.turn = reader:ReadByte(); --轉生次數

  return self;
end