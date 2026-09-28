
DataFightGoalData = {}
DataFightGoalData.__index = DataFightGoalData;

function DataFightGoalData.New(reader)
  local self = {};
  setmetatable(self, DataFightGoalData);
  
  self.score = reader:ReadUInt16();
  self.itemId = reader:ReadUInt16();
  self.count = reader:ReadUInt16();
  
  return self;
end

DataFightAwardData = {}
DataFightAwardData.__index = DataFightAwardData;

function DataFightAwardData.New(reader)
  local self = {};
  setmetatable(self, DataFightAwardData);
  
  local id = reader:ReadUInt16();
  local kind = reader:ReadByte();

  local rank = reader:ReadUInt16();
  self.awards = {};
  for i = 1, 3 do
    self.awards[i] = {};
    self.awards[i].itemId = reader:ReadUInt16();
    self.awards[i].count = reader:ReadUInt32();
  end
  
  return kind, rank, self;
end
