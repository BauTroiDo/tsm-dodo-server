LightningStroke = InheritsFromBaseAttackSkill();

function LightningStroke:InitExtraParameters()
  self.rockLight = {};
  self.flashLight = {};
end

function LightningStroke:ExtraClose()
  ClearLightTable(self.rockLight);
  ClearLightTable(self.flashLight);
  self.isLightEnd = true;
end

function LightningStroke:Update()
  local me = FightField.fightHum[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];

  if self.step == 1 then
    local  newPos = me:GetMagicPos();
    me:SetMoment(newPos.x, newPos.y, false);
    me.roleController:SetAnimationForceId(ERolePose.Magic);
    local CirclePic = self:GetCirclePic();
    FightField.attack[self.humIdx]:SetLightWithOrder(1, EEffectLightDis.Ground, CirclePic, 100, 1, 20, 20, EEffectLightTracer.StandAni, me.nowPos.x, me.nowPos.y, 20, true, 80);
    self.interval = 0.1;
    self.step = 2;
  elseif self.step == 2 then
    if self:CheckInterval() then 
      local newPos = target:RCtoSpot(target.colm, 0, EFightSpot.None, 0);
      local newPos1 = target:RCtoSpot(target.colm, 4, EFightSpot.None, 0);
      local LightningPic = self:GetLightningPic();
      local RockPic = self:GetRockPic();
      for i = 0, 1 do
        self.flashLight[i] = EffectLight.New(LightningPic, 90, 1, 4, 4, EEffectLightTracer.Line, newPos.x + (i * 20), newPos.y - (i * 20), 30, true, 0, newPos1.x, newPos1.y, 0.32);
        self.flashLight[i + 2] = EffectLight.New(LightningPic, 90, 1, 4, 4, EEffectLightTracer.Line, newPos1.x - (i * 20), newPos1.y + (i * 20), 30, true, 0, newPos.x, newPos.y, 0.32);
        self.flashLight[i]:SetTimeScale(FightField.timeScale);  
        self.flashLight[i + 2]:SetTimeScale(FightField.timeScale);  
        self.flashLight[i]:SetSortingOrder(FightField.FIGHT_BODYLIGHT_ORDER);
        self.flashLight[i + 2]:SetSortingOrder(FightField.FIGHT_BODYLIGHT_ORDER);

        self.rockLight[i] = EffectLight.New(RockPic, 33, 8, 15, 15, EEffectLightTracer.Line, newPos.x + (i * 20), newPos.y - (i * 20), 50, true, 0, newPos1.x, newPos1.y + 30, 0.32);
        self.rockLight[i]:SetTimeScale(FightField.timeScale);  
        self.rockLight[i]:SetSortingOrder(FightField.FIGHT_BODYLIGHT_ORDER);
        self.rockLight[i + 2] = EffectLight.New(RockPic, 33, 8, 15, 15, EEffectLightTracer.Line, newPos1.x - (i * 20), newPos1.y + (i * 20), 50, true, 0, newPos.x, newPos.y + 30, 0.32);
        self.rockLight[i + 2]:SetTimeScale(FightField.timeScale);  
        self.rockLight[i + 2]:SetSortingOrder(FightField.FIGHT_BODYLIGHT_ORDER);
      end

      local SmokePic = self:GetSmokePic();
      FightField.attack[self.humIdx]:SetLightWithOrder(4, EEffectLightDis.Body, SmokePic, 90, 1, 8, 16, EEffectLightTracer.Line, newPos.x + 20, newPos.y + 30, 30, true, 0, newPos1.x + 20, newPos1.y + 30, 0.32);
      FightField.attack[self.humIdx]:SetLightWithOrder(5, EEffectLightDis.Body, SmokePic, 90, 1, 8, 16, EEffectLightTracer.Line, newPos1.x + 20, newPos1.y + 30, 30, true, 0, newPos.x + 20, newPos.y + 30, 0.32);
      self.step = 3;
    end
  elseif self.step == 3 then
    if FightField.attack[self.humIdx].lights[4].picId == 0 then
      me:PlaySound(target.colm, target.row);
      for i=0, self.emyNum-1 do
        target = FightField.fightHum[self.emyIdxAy[i]];
        me:PlayEffect(target.colm, target.row, ERolePose.Hit);
        target.roleController:SetAnimationForceId(ERolePose.Hit);
        target:SetSpeed(0.16);

        if not target:IsInSealStatus() then
          local newPos = target:GetSpot(math.random(4), math.random(0, 30) + 30);
          target:SetJump(newPos.x, newPos.y, true);
        end
      end
      self.step = 4;
    end
  elseif self.step == 4 then
    if target.arrive then
      for i=0, self.emyNum-1 do
        target = FightField.fightHum[self.emyIdxAy[i]];
        target.roleController:SetAnimationForceId(ERolePose.None);
        target.roleController:SetIsDead(true);
      end
      self.interval = 0.8;
      self.step = 5;
    end
  elseif self.step == 5 then
    if self:CheckInterval() then 
      self.isLightEnd =true;
      self.step = 6;
    end
  end
end

function LightningStroke:GetCirclePic()
  if self.showKind == ESkill_Kind.LightningStroke then
    return 10078;
  elseif self.showKind == ESkill_Kind.LightningBurst  then
    return 11032;
  end
end

function LightningStroke:GetLightningPic()
  if self.showKind == ESkill_Kind.LightningStroke then
    return 10069;
  elseif self.showKind == ESkill_Kind.LightningBurst  then
    return 11033;
  end
end

function LightningStroke:GetRockPic()
  if self.showKind == ESkill_Kind.LightningStroke then
    return 10008;
  elseif self.showKind == ESkill_Kind.LightningBurst  then
    return 11034;
  end
end

function LightningStroke:GetSmokePic()
  if self.showKind == ESkill_Kind.LightningStroke then
    return 10008;
  elseif self.showKind == ESkill_Kind.LightningBurst  then
    return 11034;
  end
end

--專武技能
EW_LightningStroke = InheritsAttack(LightningStroke);

function EW_LightningStroke:InitExtraParameters()
  self.rockLight = {};
  self.flashLight = {};
end

function EW_LightningStroke:InitDrawBlack()
  self.needDrawBlack = true;   
end

function EW_LightningStroke:ExtraClose()
  ClearLightTable(self.rockLight);
  ClearLightTable(self.flashLight);
  self.isLightEnd = true;
end


--單體雷擊
Lightning = InheritsFromBaseAttackSkill();

function Lightning:InitExtraParameters()
  self.rockLight = {};
  self.flashLight = {};
end

function Lightning:ExtraClose()
  ClearLightTable(self.rockLight);
  ClearLightTable(self.flashLight);
  self.isLightEnd = true;
end

function Lightning:Update()
  local me = FightField.fightHum[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];

  if self.step == 1 then
    me.roleController:MagicAttack();
    self.interval = 0.1;
    self.step = 2;
  elseif self.step == 2 then
    if self:CheckInterval() then 
      local newPos = target.nowPos;
      FightField.attack[self.humIdx]:SetLightWithOrder(4, EEffectLightDis.Body, 10069, 90, 1, 4, 4, EEffectLightTracer.StandAni, newPos.x + 20, newPos.y + 30, 30, true);
      FightField.attack[self.humIdx]:SetLightWithOrder(5, EEffectLightDis.Body, 10008, 33, 8, 15, 15, EEffectLightTracer.StandAni, newPos.x + 20, newPos.y + 30, 30, true);
      FightField.attack[self.humIdx]:SetLightWithOrder(6, EEffectLightDis.Body, 10133, 90, 1, 8, 16, EEffectLightTracer.StandAni, newPos.x + 20, newPos.y + 30, 30, true);      
      self.step = 3;
    end
  elseif self.step == 3 then
    if FightField.attack[self.humIdx].lights[4].picId == 0 then
      me:PlaySound(target.colm, target.row);

      me:PlayEffect(target.colm, target.row, ERolePose.Hit);
      target.roleController:SetAnimationForceId(ERolePose.Hit);
      target:SetSpeed(0.24);

      if not target:IsInSealStatus() then
        local newPos = target:GetSpot(math.random(4), math.random(0, 30) + 30);
        target:SetJump(newPos.x, newPos.y, true);
      end

      self.step = 4;
    end
  elseif self.step == 4 then
    if target.arrive then
      target.roleController:SetAnimationForceId(ERolePose.None);
      target.roleController:SetIsDead(true);

      self.interval = 0.8;
      self.step = 5;
    end
  elseif self.step == 5 then
    if self:CheckInterval() then 
      self.isLightEnd =true;
      self.step = 6;
    end
  end
end