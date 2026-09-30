# Shared Citizen Production Action Design

## Goal

Use one abstract production action for farming and mining so both jobs follow the same skill, need-utility, market-memory, travel, scheduling, duration, and decision-score rules. Keep each job's world-specific eligibility and production behavior in its concrete action.

## Behavior

- A production outcome carries an item, quantity, probability, and whether the available site resource can pay for it.
- Expected output utility is the sum of each affordable outcome's probability multiplied by its need utility.
- Need utility is recalculated from the citizen's current eat, entertainment, and safety needs.
- If an output item matches the citizen's remembered market request, one output unit uses the remembered received item's current need utility minus the requested item's current need utility. Remaining units use direct need utility.
- Net utility subtracts consumed-input utility where an action consumes inputs.
- The shared score is `max(0, net expected utility) / (skill-adjusted work ticks + travel ticks)`, with the existing schedule preference applied to farm or mine work when scheduled.
- Skill level determines work duration using the existing `1.02 ^ level` speed multiplier and one-tick minimum.
- Farming has unbounded resource capacity: it never depletes a numeric site-stock counter. It still requires a ripe crop registered to the citizen's household and enough inventory capacity for the wheat yield.
- Mining keeps its finite worksite stock. An output outcome is excluded when that drop's resource cost exceeds the site's remaining stock.
- The decisions profile continues to show each action's score and expanded calculation details from the same production evaluation used by the planner.

## Structure

- Add an abstract production action that owns reusable output-utility evaluation, skill-adjusted duration, score calculation, and score-detail generation.
- The abstract action exposes production inputs such as outcome distribution, consumed inputs, skill, base work ticks, travel ticks, and schedule status through protected hooks or an immutable evaluation profile.
- `CitizenFarmGoal` supplies its household crop eligibility, two-wheat output, farming skill, travel estimate, and scheduled-work flag.
- `CitizenMiningGoal` supplies worksite discovery/eligibility, weighted drop outcomes, finite stock affordability, mining skill, travel estimate, and scheduled-work flag.
- Concrete goals retain their existing world mutations: farming harvests a ripe wheat crop; mining consumes worksite resource points and carries affordable loot.
- The decision planner consumes the shared production score without adding action-specific fixed utility bonuses.

## Compatibility and scope

- Preserve current citizen skills, market-memory fields, inventory behavior, schedule, work durations, and save data.
- No save migration is needed.
- No generic production engine is introduced for non-production actions such as sleep, delivery, market visits, or returning home.

## Acceptance

- Farm and mine use the same shared output-utility and score implementation.
- Changing needs changes both jobs' output utility; changing the remembered market target changes the matching output's trade-adjusted utility.
- Farm outcomes remain available without a finite stock counter but require a ripe owned crop and carry capacity.
- Mining outcomes remain probability-weighted and are omitted when the site cannot afford them.
- Skill, travel, schedule preference, durations, and displayed breakdown agree with the selected action's score.
- Existing work execution, crop ownership, mining stock consumption, and citizen save data remain intact.
