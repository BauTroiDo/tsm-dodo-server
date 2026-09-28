LightDisStatus = InheritsFromBaseAttackSkill();

function LightDisStatus:Update()
  local me = FightField.fightHum[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];

  if self.step == 1 then
    FightField.attack[self.humIdx]:SetLightWithOrder(1, EEffectLightDis.Ground, 10824, 50, 1, 32, 32, EEffectLightTracer.StandAni, me.nowPos.x + 7, me.nowPos.y, 20, true, 50);
    self.interval = 1;
    self.step = 2;
  elseif self.step == 2 then
    if self:CheckInterval() then 
      for i = 0, self.emyNum - 1 do        
        target = FightField.fightHum[self.emyIdxAy[i]];
        FightField.attack[self.humIdx]:SetLightWithOrder(2 + i, EEffectLightDis.Body, 10825, 80, 1, 19, 19, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y, 30, false, 60);
      end
      self.step = 3;
    end
  elseif self.step == 3 then
    if self.emyNum == 0 then
      self.step = 4;
      return;
    end

    if FightField.attack[self.humIdx].lights[2].picId == 0 then
      self.step = 4;
    end
  elseif self.step == 4 then
    self.isLightEnd =true;
    self.step = 5;
  end
end

