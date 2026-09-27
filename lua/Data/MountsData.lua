MountsData = {}
MountsData.__index = MountsData;

function MountsData.New(reader)
  local self = {};
  setmetatable(self, MountsData);

  local id = reader:ReadUInt16();
  
  self.npcId = id;
  self.flagId = reader:ReadUInt16();
  self.source = reader:ReadUInt32();
  self.scale = reader:ReadByte() * 0.01;

  return id, self;
end

MountsGrowData = {}
MountsGrowData.__index = MountsGrowData;

function MountsGrowData.New(reader)
  local self = {};
  setmetatable(self, MountsGrowData);

  local level = reader:ReadByte();

  self.speed = reader:ReadByte();

  self.upItemId = reader:ReadUInt16();
  self.upItemCount = reader:ReadByte();
  self.upMoney = reader:ReadUInt32();

  self.attributes = {};
  
  for i = 1, 5 do
    self.attributes[i] = {};
    self.attributes[i].addValue = reader:ReadUInt16();
    self.attributes[i].upItemId = reader:ReadUInt16();
    self.attributes[i].upItemCount = reader:ReadUInt16();
  end

  return level, self;
end