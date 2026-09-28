LoudShout = InheritsFromBaseAttackSkill();

function LoudShout:Update()
  local me = FightField.fightHum[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];

  if self.step == 1 then
    local newPos = me:GetMagicPos();
    me:SetMoment(newPos.x, newPos.y, false);
    me.roleController:MagicAttack();
    --030601J八卦魔法陣
    FightField.attack[self.humIdx]:SetLightWithOrder(1, EEffectLightDis.Ground, 10079, 50, 1, 22, 22, EEffectLightTracer.StandAni, me.nowPos.x + 5, me.nowPos.y, 30, false, 113);
    self.interval = 0.8;
    self.step = 2;
  elseif self.step == 2 then
    if self:CheckInterval() then 
      if target.party_Kind == EFightParty.Left then
        FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky1, 10074, 150, 1, 8, 8, EEffectLightTracer.Stand, me.nowPos.x - 20, me.nowPos.y, 30, false, 0);
      else
        FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky1, 10075, 150, 1, 8, 8, EEffectLightTracer.Stand, me.nowPos.x + 20, me.nowPos.y, 30, false, 50);
      end      
      FightField.attack[self.humIdx].lights[EEffectLightKind.Sky1]:SetAlpha(1, 1, 30);
      self.step = 3;
    end
  elseif self.step == 3 then
    if FightField.attack[self.humIdx].lights[EEffectLightKind.Sky1].orderCount == 7 then
      if target.party_Kind == EFightParty.Left then
        FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky2, 10074, 150, 8, 8, 8, EEffectLightTracer.Line, me.nowPos.x - 20, me.nowPos.y, 30, false, 0, target.nowPos.x, target.nowPos.y, 0.66);
      else
        FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky2, 10075, 150, 8, 8, 8, EEffectLightTracer.Line, me.nowPos.x + 20, me.nowPos.y, 30, false, 50, target.nowPos.x, target.nowPos.y, 0.66);
      end

      FightField.attack[self.humIdx].lights[EEffectLightKind.Sky1]:SetAlpha(40, -2, 20);
      FightField.attack[self.humIdx].lights[EEffectLightKind.Sky2]:SetAlpha(10, 3, 20);      
      me:PlaySound(target.colm, target.row, Attack_Start);
      FightField.SetShake(true);
      self.step = 4;
    end
  elseif self.step == 4 then
    if FightField.attack[self.humIdx].lights[EEffectLightKind.Sky2].isArrive then
      FightField.SetShake(false);
      me:PlaySound(target.colm, target.row);
      self.isLightEnd = true;
      self.step = 5;
    end
  end
end

--專武技能
EW_LoudShout = InheritsAttack(LoudShout);

function EW_LoudShout:ExtraClose()
  FightField.SetShake(false);
end

function EW_LoudShout:InitDrawBlack()
  self.needDrawBlack = true;   
end