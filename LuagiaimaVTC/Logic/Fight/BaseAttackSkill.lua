function InheritsFromBaseAttackSkill()  
  return InheritsAttack(BaseAttackSkill);
end

function InheritsAttack(parentTable)
  local newTable = table.Copy(parentTable);
  newTable.__index = newTable;
  function newTable.New(mainIdx, targetIdx, fightArea, showKind)
    local self = newTable.Create(mainIdx, targetIdx, fightArea, showKind);
    setmetatable(self, newTable);
    self:InitExtraParameters();
    self:InitDrawBlack();
    self:HandleDrawBlack();
    return self;
  end
  return newTable;
end

function ClearLightTable(lights)
  for k, v in pairs(lights) do
    if v ~= nil then
      v:Stop();
    end
  end
  table.Clear(lights);
end

BaseAttackSkill = {};
BaseAttackSkill.__index = BaseAttackSkill;

function BaseAttackSkill.Create(mainIdx, targetIdx, fightArea, showKind)
  local self = {};
  setmetatable(self, BaseAttackSkill);

  self.humIdx = mainIdx;
  self.tarIdx = targetIdx;
  self.interval = 0;
  self.isLightEnd = false;
  self.step = 1;
  self.showKind = showKind;

  self.emyNum = 0;
  self.emyIdxAy = {};
  if fightArea ~= nil and fightArea > 0 then 
    self.emyNum, self.emyIdxAy = FightField.GetEnemyIdx(fightArea, self.tarIdx);
  end
  return self;
end

function BaseAttackSkill:InitExtraParameters()
end

function BaseAttackSkill:InitDrawBlack()
  self.needDrawBlack = false;     --需要背景塗黑在子類別中改寫為true
end

function BaseAttackSkill:HandleDrawBlack()
  if self.needDrawBlack then 
    FightField.SetDrawBlack(0, 200, 50);
  end
end

function BaseAttackSkill:ExtraClose()
end

function BaseAttackSkill:Free()
  if self.needDrawBlack then 
    FightField.CloseDrawBlack();
  end
  FightField.RestoreFightInfo(self.humIdx);
  if self.emyNum > 0 then 
    for k,v in pairs(self.emyIdxAy) do
      FightField.RestoreFightInfo(v);
    end
  else
    FightField.RestoreFightInfo(self.tarIdx);
  end
  table.Clear(self.emyIdxAy);
end

function BaseAttackSkill:Update()
end

function BaseAttackSkill:CheckInterval()
  if self.interval >= 0 then
    self.interval = self.interval - CGTimer.deltaTime * FightField.timeScale;
    return false;
  end
  return true;
end

