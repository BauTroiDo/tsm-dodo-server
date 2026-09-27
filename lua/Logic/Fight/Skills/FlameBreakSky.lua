FlameBreakSky = InheritsFromBaseAttackSkill();

function FlameBreakSky:ExtraClose()
  if FightField.fightHum[self.tarIdx] ~= nil and FightField.fightHum[self.tarIdx].roleController ~= nil then
    FightField.fightHum[self.tarIdx].roleController.gameObject:SetActive(true);
  end
end

function FlameBreakSky:Update()
  local me = FightField.fightHum[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];

  if self.step == 1 then
    local newPos = me:GetMagicPos();
    me:SetMoment(newPos.x, newPos.y, false);
    me.roleController:MagicAttack();
    FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Ground, 11127, 100, 1, 20, 20, EEffectLightTracer.StandAni,
      me.nowPos.x + 5, me.nowPos.y, 20, true, 80);
    self.interval = 1.0;
    self.step = 2;
  elseif self.step == 2 then
    if self:CheckInterval() then
      FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky1, 11128, 140, 1, 4, 4, EEffectLightTracer.StandAni,
        target.nowPos.x - 18, target.nowPos.y - 30, 30, false, 0);
      FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky2, 11129, 140, 1, 4, 4, EEffectLightTracer.StandAni,
        target.nowPos.x + 10, target.nowPos.y, 30, false, 30);
      FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky3, 11130, 60, 1, 24, 24, EEffectLightTracer.Line,
        target.nowPos.x - 18, target.nowPos.y - 400, 35, true, 20, target.nowPos.x + 10, target.nowPos.y, 0.56);
      me:PlaySound(target.colm, target.row);
      me:PlayEffect(target.colm, target.row, ERolePose.Hit);
      target.roleController.gameObject:SetActive(false);
      self.interval = 1.0;
      self.step = 3;
    end
  elseif self.step == 3 then
    if self:CheckInterval() then
      if FightField.attack[self.humIdx].lights[EEffectLightKind.Sky1].picId == 0 then
        FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky1, 11128, 140, 1, 4, 4, EEffectLightTracer.StandAni,
          target.nowPos.x - 18, target.nowPos.y - 30, 30, false, 0);
        FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky2, 11129, 140, 1, 4, 4, EEffectLightTracer.StandAni,
          target.nowPos.x + 10, target.nowPos.y, 30, false, 30);
        self.interval = 1.0;
        self.step = 4;
      end
    end
  elseif self.step == 4 then
    if self:CheckInterval() then
      if FightField.attack[self.humIdx].lights[EEffectLightKind.Sky1].picId == 0 then
        FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky1, 11128, 140, 1, 4, 4, EEffectLightTracer.StandAni,
          target.nowPos.x - 18, target.nowPos.y - 30, 30, false, 0);
        FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky2, 11129, 140, 1, 4, 4, EEffectLightTracer.StandAni,
          target.nowPos.x + 10, target.nowPos.y, 30, false, 30);
        FightField.attack[self.humIdx].lights[EEffectLightKind.Sky1]:SetAlpha(20, -1, 20);
        FightField.attack[self.humIdx].lights[EEffectLightKind.Sky2]:SetAlpha(20, -1, 20);
        target.roleController.gameObject:SetActive(true);
        target.roleController:Hit();
        FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky1, 11131, 30, 1, 16, 16, EEffectLightTracer.StandAni,
          target.nowPos.x, target.nowPos.y, 35, true, 50);
        FightField.attack[self.humIdx].lights[EEffectLightKind.Sky3]:SetAlpha(15, 1, 20);
        self.interval = 1.0;
        self.step = 5;
      end
    end
  elseif self.step == 5 then
    if FightField.attack[self.humIdx].lights[EEffectLightKind.Sky1].picId == 0 then
      self.isLightEnd = true;
      self.step = 6;
    end
  end
end
