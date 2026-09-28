FlyFireRandom = InheritsAttack(RandomHit)

function FlyFireRandom:PlayEffectLight()
  local target = FightField.fightHum[self.aimIdx];
  local me = FightField.fightHum[self.humIdx];
  local pic = self:GetEffectLigthPic();
  FightField.attack[self.humIdx]:SetLightWithOrder(1, EEffectLightDis.Body, pic, 50, 1, 10, 10, EEffectLightTracer.StandAni, me.nowPos.x, me.nowPos.y, 25, true, 20);
  if target.party_Kind == EFightParty.Left then
    pic = self:GetEffectLigthPicLeft(self.attackStep);
    if self.attackStep == 1 or self.attackStep == 3 then
      FightField.attack[self.humIdx]:SetLightWithOrder(3, EEffectLightDis.Sky, pic, 50, 1, 4, 4, EEffectLightTracer.StandAni, target.nowPos.x + 30, target.nowPos.y, 35, true, 120);
    elseif self.attackStep == 2 then
      FightField.attack[self.humIdx]:SetLightWithOrder(3, EEffectLightDis.Sky, pic, 50, 1, 4, 4, EEffectLightTracer.StandAni, target.nowPos.x - 60, target.nowPos.y, 35, true, 80);
    end
  else
    pic = self:GetEffectLigthPicRight(self.attackStep);
    if self.attackStep == 1 or self.attackStep == 3 then
      FightField.attack[self.humIdx]:SetLightWithOrder(3, EEffectLightDis.Sky, pic, 50, 1, 4, 4, EEffectLightTracer.StandAni, target.nowPos.x - 60, target.nowPos.y, 35, true, 80);
    elseif self.attackStep == 2 then
      FightField.attack[self.humIdx]:SetLightWithOrder(3, EEffectLightDis.Sky, pic, 50, 1, 4, 4, EEffectLightTracer.StandAni, target.nowPos.x + 30, target.nowPos.y, 35, true, 120);
    end
  end      
end

--置換光影
function FlyFireRandom:GetEffectLigthPic()
  if self.showKind ==  ESkill_Kind.FlyFireRandom then
    return 10092;
  elseif self.showKind == ESkill_Kind.BurstCutX then
    return 11012;
  end
end

function FlyFireRandom:GetEffectLigthPicLeft(attackStep)
  if self.showKind ==  ESkill_Kind.FlyFireRandom then
    if attackStep % 2 == 0 then
      return 10149;
    else
      return 10148;
    end
  elseif self.showKind == ESkill_Kind.BurstCutX then
    if attackStep % 2 == 0 then
      return 11014
    else
      return 11013;
    end
  end
end

function FlyFireRandom:GetEffectLigthPicRight(attackStep)
  if self.showKind ==  ESkill_Kind.FlyFireRandom then
    if attackStep % 2 == 0 then
      return 10148;
    else
      return 10149;
    end
  elseif self.showKind == ESkill_Kind.BurstCutX then
    if attackStep % 2 == 0 then
      return 11013;
    else
      return 11014;
    end
  end
end