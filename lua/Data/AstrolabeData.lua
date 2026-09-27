AstrolabeData = {}
AstrolabeData.__index = AstrolabeData;

function AstrolabeData.New(reader)
  local self = {};
  setmetatable(self, AstrolabeData);
  
  local index = reader:ReadByte();

  self.attributes = {};
  for i = 1, Astrolabe.maxAstrolabeLv do
    self.attributes[i] = {};
    self.attributes[i].value = reader:ReadByte();
    self.attributes[i].needPoint = reader:ReadByte();
  end
  
  return index, self;
end

