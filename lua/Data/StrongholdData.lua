ArmyMoralColor = {
  Yellow = 0,
  Red = 1,
  Blue = 2
}

ArmyBattleCity = {
  Village = 3,
  Camp = 8,
  Fort = 12,
  Capital = 15
}

StrongholdData = {}
StrongholdData.__index = StrongholdData;

function StrongholdData.New(reader)
  local self = {};
  setmetatable(self, StrongholdData);

  self.id = reader:ReadByte(); --編號
  self.nameString = reader:ReadUInt16(); --名稱

  self.pos = {}; --地圖上位置
  self.pos.x = reader:ReadInt16();
  self.pos.y = reader:ReadInt16();

  self.score = reader:ReadByte(); --分數
  self.moral = reader:ReadByte(); --陣營(1 = 紅,2 = 藍)
  self.count = reader:ReadByte(); --防守位置數量

  self.neighbor = {}; --相鄰點
  for i = 1, 6 do
    self.neighbor[i] = reader:ReadByte();
  end

  self.selfPoint = reader:ReadByte(); --1=紅隊出兵處、2=籃隊出兵處
  self.pointType = reader:ReadByte(); --0=一般，1=戰鼓

  return self.id, self;
end

function StrongholdData:GetRotation()
  if self.moral == ArmyMoralColor.Red then
    return Vector3.New(-1, 1,0);
  elseif self.moral == ArmyMoralColor.Blue then
    return Vector3.New(1, 1, 0);
  else
    return Vector3.New(1, 1, 0);
  end
end

function StrongholdData:GetHPSprite()
  if self.moral == ArmyMoralColor.Red then
    return UI.GetSprite("com001");
  elseif self.moral == ArmyMoralColor.Blue then
    return UI.GetSprite("com002");
  elseif self.moral == ArmyMoralColor.Yellow then
    return UI.GetSprite("com111");
  end
end

function StrongholdData:GetFlagSprite()
  if self.moral == ArmyMoralColor.Red then
    return UI.GetSprite("icon181");
  elseif self.moral == ArmyMoralColor.Blue then
    return UI.GetSprite("icon180");
  elseif self.moral == ArmyMoralColor.Yellow then
    return UI.GetSprite("icon207");
  end
end

function StrongholdData:GetCitySprite()
  if self.moral == ArmyMoralColor.Red then
    if self.score == ArmyBattleCity.Village then
      return UI.GetSprite("but191");
    elseif self.score == ArmyBattleCity.Camp then
      return UI.GetSprite("but309D");
    elseif self.score == ArmyBattleCity.Fort then
      return UI.GetSprite("but194");
    elseif self.score == ArmyBattleCity.Capital then
      return UI.GetSprite("but311D");
    end
  elseif self.moral == ArmyMoralColor.Blue then
    if self.score == ArmyBattleCity.Village then
      return UI.GetSprite("but192");
    elseif self.score == ArmyBattleCity.Camp then
      return UI.GetSprite("but310D");
    elseif self.score == ArmyBattleCity.Fort then
      return UI.GetSprite("but195");
    elseif self.score == ArmyBattleCity.Capital then
      return UI.GetSprite("but312D");
    end
  elseif self.moral == ArmyMoralColor.Yellow then
    return UI.GetSprite("but323");
  end
end

function StrongholdData:GetTypeByScore()
  if self.score == ArmyBattleCity.Village then
    return StrongholdCityType.Village;
  elseif self.score == ArmyBattleCity.Camp then
    return StrongholdCityType.Camp;
  elseif self.score == ArmyBattleCity.Fort then
    return StrongholdCityType.Fort;
  elseif self.score == ArmyBattleCity.Capital then
    return StrongholdCityType.Capital;
  else
    return StrongholdCityType.DrumHouse;
  end
end

function StrongholdData:GetPos()
  return self.pos.x, self.pos.y;
end

function StrongholdData:GetLine()
  local points = {};

  for i = 1, 6 do
    if self.neighbor[i] ~= 0 then
      local tempPoints = {};
      tempPoints.index = self.neighbor[i];
      tempPoints.pos = {};
      tempPoints.pos[1] = {self:GetPos()};
      tempPoints.pos[2] = {strongholdDatas[self.neighbor[i]]:GetPos()};

      table.insert(points, tempPoints);
    end
  end

  return points;
end

function StrongholdData:GetSliderPosY()
  if self.score == ArmyBattleCity.Village then
    return -22;
  elseif self.score == ArmyBattleCity.Fort then
    return -10;
  else
    return 0;
  end
end

function StrongholdData:GetButtonPosY()
  if self.score == ArmyBattleCity.Village then
    return 40;
  elseif self.score == ArmyBattleCity.Fort then
    return 20;
  else
    return 0;
  end
end

