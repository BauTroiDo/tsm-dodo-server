SnowWintwer = InheritsFromBaseAttackSkill();

function SnowWintwer:Update()  
  local me = FightField.fightHum[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];

  if self.step == 1 then           
    me.roleController:MagicAttack();
    local pic = self:GetStepOnePic();
    FightField.attack[self.humIdx]:SetLightWithOrder(1, EEffectLightDis.Ground, pic, 40, 1, 32, 32, EEffectLightTracer.StandAni, me.nowPos.x + 7, me.nowPos.y, 20, true, 50);
    self.interval = 0.5;
    self.step = 2;      
  elseif self.step == 2 then
    if self:CheckInterval() then 
      local fieldPos = Vector2.New(FightField.skillOffset.x, FightField.skillOffset.y);
      local pic = self:GetStepTwoPic();
      if target.party_Kind == EFightParty.Left then
        for i = 0, 4 do
          FightField.attack[self.humIdx]:SetLightWithOrder(4 + i * 2, EEffectLightDis.Body, pic, 100 + math.random(20), 1, 10, 10, EEffectLightTracer.StandAni, fieldPos.x + 120 + (i * 75), fieldPos.y + 300 - (i * 40), 20, true, 40);
          FightField.attack[self.humIdx]:SetLightWithOrder(5 + i * 2, EEffectLightDis.Body, pic, 100 + math.random(20), 1, 10, 10, EEffectLightTracer.StandAni, fieldPos.x + 150 + (i * 75), fieldPos.y + 340 - (i * 40), 20, true, 40);
        end
      else
        for i = 0, 4 do
          FightField.attack[self.humIdx]:SetLightWithOrder(4 + i * 2, EEffectLightDis.Body, pic, 100 + math.random(20), 1, 10, 10, EEffectLightTracer.StandAni, fieldPos.x + 380 + (i * 75), fieldPos.y + 480 - (i * 40), 20, true, 40);
          FightField.attack[self.humIdx]:SetLightWithOrder(5 + i * 2, EEffectLightDis.Body, pic, 100 + math.random(20), 1, 10, 10, EEffectLightTracer.StandAni, fieldPos.x + 440 + (i * 75), fieldPos.y + 520 - (i * 40), 20, true, 40);
        end
      end

      local newPos = me:GetMagicPos();
      local stone = self:GetSnowStonePic(1);
      local stoneAirFlow = self:GetSnowStonePic(2);
      FightField.attack[self.humIdx]:SetLightWithOrder(2, EEffectLightDis.Sky, stone, 100, 1, 8, 8, EEffectLightTracer.Line, newPos.x, fieldPos.y, 255, true, 0, newPos.x, newPos.y - 80, 0.33);
      FightField.attack[self.humIdx]:SetLightWithOrder(3, EEffectLightDis.Sky, stoneAirFlow, 40, 1, 13, 13, EEffectLightTracer.Line, newPos.x, fieldPos.y, 30, true, 0, newPos.x, newPos.y - 60, 0.33);
      self.step = 3;
    end
  elseif self.step == 3 then
    if FightField.attack[self.humIdx].lights[2].isArrive then
      local newPos = me:GetMagicPos();
      local stone = self:GetSnowStonePic(1);
      local stoneAirFlow = self:GetSnowStonePic(2);
      FightField.attack[self.humIdx]:SetLightWithOrder(2, EEffectLightDis.Sky, stone, 100, 1, 8, 8, EEffectLightTracer.Stand, newPos.x, newPos.y - 80, 255, true, 0);
      FightField.attack[self.humIdx]:SetLightWithOrder(3, EEffectLightDis.Sky, stoneAirFlow, 30, 1, 13, 13, EEffectLightTracer.Stand, newPos.x, newPos.y - 60, 30, true, 0);
      
      self.interval = 0.8;
      self.step = 4;
     end
  elseif self.step == 4 or self.step == 5 then
    if self:CheckInterval() then
      local airFlowOne = self:GetAirflowPic(1);
      local airFlowTwo = self:GetAirflowPic(2);
      for i = 0, self.emyNum-1 do            
        target = FightField.fightHum[ self.emyIdxAy[i] ];
        target.roleController:SetAnimationForceId(ERolePose.Hit);
        FightField.attack[self.humIdx]:SetLightWithOrder(14 + i * self.emyNum, EEffectLightDis.Body, airFlowOne, 60, 1, 7, 7, EEffectLightTracer.StandAni, target.nowPos.x + 5, target.nowPos.y, 30, true, 50);
        FightField.attack[self.humIdx]:SetLightWithOrder(15 + i * self.emyNum, EEffectLightDis.Body, airFlowTwo, math.random(0, 80) + 10, 1, 9, 9, EEffectLightTracer.StandAni, target.nowPos.x + 5, target.nowPos.y, 30, true, 50);
      end

      self.interval = 0.8;
      self.step = self.step + 1;
     end
  elseif self.step == 6 then
    if self:CheckInterval() then 
      self.isLightEnd = true;
      self.step = 7;
    end
  end
end

function SnowWintwer:GetStepOnePic()
  if self.showKind == ESkill_Kind.SnowWintwer then
    return 10070;
  elseif self.showKind == ESkill_Kind.SkyThunder then
    return 11021;
  end
end

function SnowWintwer:GetStepTwoPic()
  if self.showKind == ESkill_Kind.SnowWintwer then
    return 10020;
  elseif self.showKind == ESkill_Kind.SkyThunder then
    return 11022;
  end
end

function SnowWintwer:GetSnowStonePic(idx)
  if self.showKind == ESkill_Kind.SnowWintwer then
    if idx == 1 then
      return 10288;
    elseif idx == 2 then
      return 10289;
    end
  elseif self.showKind == ESkill_Kind.SkyThunder then
    if idx  == 1 then
      return 11023;
    elseif idx  == 2 then
      return 11024;
    end
  end
end

function SnowWintwer:GetAirflowPic(idx)
  if self.showKind == ESkill_Kind.SnowWintwer then
    if idx == 1 then
      return 10286;
    elseif idx == 2 then
      return 10300;
    end
  elseif self.showKind == ESkill_Kind.SkyThunder then
    if idx  == 1 then
      return 11025;
    elseif idx  == 2 then
      return 11026;
    end
  end
end