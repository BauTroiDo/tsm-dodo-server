CollectCardData = {}
CollectCardData.__index = CollectCardData;

function CollectCardData.New(reader)
  local self = {};
  self.id = reader:ReadByte();
  --todo
  self.index = reader:ReadByte();
  --
  self.name = reader:ReadUInt32();
    
  self.story = reader:ReadUInt32();
  self.card = {};
  for i = 1, 9 do 
    self.card[i] = reader:ReadUInt16();
  end

  self.maxLevel = reader:ReadByte();
  
  self.levelUpParamter = {};
  for i = 1, 2 do
    self.levelUpParamter[i] = reader:ReadByte();
  end

  self.values = {};

  for i = 1, 6 do 
    self.values[i] = {};
    self.values[i].kind = reader:ReadByte();
    self.values[i].value = reader:ReadUInt16();
    self.values[i].grow = reader:ReadUInt16();
  end

  return self;
end
