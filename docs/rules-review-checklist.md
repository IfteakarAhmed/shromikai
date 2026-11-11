# Rules review checklist (P6)

Reviewer: a team member (not a lawyer). The author of every rule is Claude (draft, phase P6).
A rule is evaluated only when `status: active` and `reviewed_by` names a person other than the author
(FR-36, R-10). **All rules below are `draft`.** The drafter never sets `reviewed_by`.

## How to review one rule

1. Open the official portal page (link below) and find the section.
2. Check that the quote is in the section, word for word. (Some stored words use an older Unicode form
   of the same letter; the verifier compares after normalisation, so that is not an error.)
3. Check that the number and unit in the rule are what the quote says.
4. Read the "Also check" note: it lists exemptions the rule cannot see.
5. Check the severity against the rubric: CRITICAL = conflicts with a mandatory provision with loss of
   wages, benefits or employment protection; HIGH = conflicts with a mandatory provision with another
   effect; MEDIUM = omission, ambiguity or interaction; LOW = ILO or drafting practice only.
6. Tell Claude "rule <id> reviewed". Claude then sets `reviewed_by` to your name with the note
   "team member, not a lawyer" and `status: active`. Claude does not do this on its own.

The application refuses to start if an active rule's quote is not in the current section text, so a
mistake here is caught at startup, not in a user's result. `./gradlew checkRules` runs the same
check for every rule, draft or active, against the corpus in your local database (it reported all 8
quotes found when the rules were drafted).

Corpus release at drafting: `2026.11.11-1`.

## Rules

| # | Rule id | Section key | Limit | Severity | Reviewed? |
| --- | --- | --- | --- | --- | --- |
| 1 | `bd-daily-hours-max` | `BLA-2006:100@original` | MAX 8 HOURS_PER_DAY | HIGH | [ ] |
| 2 | `bd-weekly-hours-max` | `BLA-2006:102@original` | MAX 48 HOURS_PER_WEEK | HIGH | [ ] |
| 3 | `bd-overtime-rate-min` | `BLA-2006:108@amend-2018` | MIN 2 MULTIPLIER | CRITICAL | [ ] |
| 4 | `bd-weekly-rest-min` | `BLA-2006:103@amend-2018` | MIN 1 COUNT | HIGH | [ ] |
| 5 | `bd-casual-leave-min` | `BLA-2006:115@original` | MIN 10 DAYS | HIGH | [ ] |
| 6 | `bd-sick-leave-min` | `BLA-2006:116@original` | MIN 14 DAYS | HIGH | [ ] |
| 7 | `bd-festival-holidays-min` | `BLA-2006:118@amend-2026` | MIN 13 DAYS | HIGH | [ ] |
| 8 | `bd-maternity-leave-min` | `BLA-2006:47@amend-2026` | MIN 120 DAYS | CRITICAL | [ ] |

### 1. `bd-daily-hours-max`

- Section key: `BLA-2006:100@original`
- Official portal page: http://bdlaws.minlaw.gov.bd/act-952/section-29427.html
- Rule: fact `DAILY_WORKING_HOURS` MAX 8 HOURS_PER_DAY; clause type `WORKING_HOURS`; severity HIGH
- Quote (as in the rule file):

  > কোন প্রাপ্তবয়স্ক শ্রমিক কোন প্রতিষ্ঠানে সাধারণতঃ দৈনিক আট ঘণ্টার অধিক সময় কাজ করিবেন না

- Also check: The text says "সাধারণতঃ" (ordinarily) and the proviso allows up to ten hours a day under section 108. Decide whether a contract that states 10 ordinary daily hours should be flagged (this rule flags any value above 8).
- [ ] Quote is in the section   - [ ] Number and unit are right   - [ ] Severity is acceptable
- Reviewed by: ________  Date: ________

### 2. `bd-weekly-hours-max`

- Section key: `BLA-2006:102@original`
- Official portal page: http://bdlaws.minlaw.gov.bd/act-952/section-29429.html
- Rule: fact `WEEKLY_WORKING_HOURS` MAX 48 HOURS_PER_WEEK; clause type `WORKING_HOURS`; severity HIGH
- Quote (as in the rule file):

  > কোন প্রাপ্তবয়স্ক শ্রমিক কোন প্রতিষ্ঠানে সাধারণতঃ সপ্তাহে আটচল্লিশ ঘণ্টার অধিক সময় কাজ করিবেন না

- Also check: Section 102(2) allows more than 48 hours subject to section 108 (cap 60 a week, 56 on average over a year). This rule flags only an ordinary weekly figure above 48.
- [ ] Quote is in the section   - [ ] Number and unit are right   - [ ] Severity is acceptable
- Reviewed by: ________  Date: ________

### 3. `bd-overtime-rate-min`

- Section key: `BLA-2006:108@amend-2018`
- Official portal page: http://bdlaws.minlaw.gov.bd/act-952/section-29435.html
- Rule: fact `OVERTIME_RATE` MIN 2 MULTIPLIER; clause type `OVERTIME`; severity CRITICAL
- Quote (as in the rule file):

  > মূল মজুরী ও মহার্ঘভাতা এবং এডহক বা অন্তবর্তী মজুরী, যদি থাকে, এর সাধারণ হারের দ্বিগুণ হারে ভাতা পাইবেন

- Also check: Check the multiplier "দ্বিগুণ" (double) and that piece-rate workers (108(2)) are outside the rule: the rule does not know a worker is piece-rate, so it may flag a piece-rate contract wrongly.
- [ ] Quote is in the section   - [ ] Number and unit are right   - [ ] Severity is acceptable
- Reviewed by: ________  Date: ________

### 4. `bd-weekly-rest-min`

- Section key: `BLA-2006:103@amend-2018`
- Official portal page: http://bdlaws.minlaw.gov.bd/act-952/section-29430.html
- Rule: fact `WEEKLY_REST_DAYS` MIN 1 COUNT; clause type `REST_AND_HOLIDAYS`; severity HIGH
- Quote (as in the rule file):

  > প্রতি সপ্তাহে কারখানা ও শিল্পের ক্ষেত্রে একদিন এবং দোকান ও প্রতিষ্ঠানের ক্ষেত্রে দেড় দিন ছুটি পাইবেন

- Also check: The floor of 1 is the factory/industry figure; shops and establishments get one and a half days. The rule does not know the establishment type, so it uses the lower figure. COUNT is whole days in the fact schema.
- [ ] Quote is in the section   - [ ] Number and unit are right   - [ ] Severity is acceptable
- Reviewed by: ________  Date: ________

### 5. `bd-casual-leave-min`

- Section key: `BLA-2006:115@original`
- Official portal page: http://bdlaws.minlaw.gov.bd/act-952/section-29442.html
- Rule: fact `CASUAL_LEAVE` MIN 10 DAYS; clause type `LEAVE`; severity HIGH
- Quote (as in the rule file):

  > প্রত্যেক শ্রমিক প্রতি পঞ্জিকা বৎসরে পূর্ণ মজুরীতে দশ দিনের নৈমিত্তিক ছুটি পাইবার অধিকারী হইবেন

- Also check: Section 115 does not apply to tea-garden workers. The rule has no worker category, so it would flag a tea-garden contract that gives fewer days.
- [ ] Quote is in the section   - [ ] Number and unit are right   - [ ] Severity is acceptable
- Reviewed by: ________  Date: ________

### 6. `bd-sick-leave-min`

- Section key: `BLA-2006:116@original`
- Official portal page: http://bdlaws.minlaw.gov.bd/act-952/section-29443.html
- Rule: fact `SICK_LEAVE` MIN 14 DAYS; clause type `LEAVE`; severity HIGH
- Quote (as in the rule file):

  > প্রত্যেক শ্রমিক প্রত্যেক পঞ্জিকা বৎসরে পূর্ণ মজুরীতে চৌদ্দ দিনের পীড়া ছুটি পাইবার অধিকারী হইবেন

- Also check: Newspaper workers have a different rule (116(2)); this rule would flag them wrongly. Check the number "চৌদ্দ" = 14.
- [ ] Quote is in the section   - [ ] Number and unit are right   - [ ] Severity is acceptable
- Reviewed by: ________  Date: ________

### 7. `bd-festival-holidays-min`

- Section key: `BLA-2006:118@amend-2026`
- Official portal page: http://bdlaws.minlaw.gov.bd/act-952/section-29445.html
- Rule: fact `FESTIVAL_HOLIDAYS` MIN 13 DAYS; clause type `REST_AND_HOLIDAYS`; severity HIGH
- Quote (as in the rule file):

  > প্রত্যেক শ্রমিককে প্রতি পঞ্জিকা বৎসরে [১৩ (তের)] দিনের মজুরীসহ উৎসব ছুটি মঞ্জুর করিতে হইবে

- Also check: The stored version is the 2026 amendment (13 days). Check on the portal that this is the version in force for the contract's date.
- [ ] Quote is in the section   - [ ] Number and unit are right   - [ ] Severity is acceptable
- Reviewed by: ________  Date: ________

### 8. `bd-maternity-leave-min`

- Section key: `BLA-2006:47@amend-2026`
- Official portal page: http://bdlaws.minlaw.gov.bd/act-952/section-26453.html
- Rule: fact `MATERNITY_LEAVE` MIN 120 DAYS; clause type `MATERNITY_BENEFIT`; severity CRITICAL
- Quote (as in the rule file):

  > প্রাপ্য প্রসূতি কল্যাণ সুবিধাসহ ১২০ (একশত বিশ) দিন পর্যন্ত কাজে অনুপস্থিত থাকিবার জন্য অনুমতি প্রদান করিবেন

- Also check: The 120 days are in section 47(3)(ক) and include 60 days before and 60 days after (section 46). Eligibility (six months of service, fewer than two living children) is in section 46 and is not checked by the rule. Weeks are converted at 7 days, so 16 weeks (112 days) is flagged.
- [ ] Quote is in the section   - [ ] Number and unit are right   - [ ] Severity is acceptable
- Reviewed by: ________  Date: ________

## Left out on purpose

These topics have legal limits in the corpus but are not drafted as rules, because a single number
would sometimes be wrong. The risk agent still looks at them and its citations are verified.

| Topic | Section | Why no rule |
| --- | --- | --- |
| Notice by employer and by worker | `BLA-2006:26@original`, `BLA-2006:27@amend-2026` | The period depends on the worker category (permanent or temporary, monthly or not) and the sections allow pay instead of notice. The corpus has no worker-category terms yet, so a rule would give false findings. |
| Probation length | `BLA-2006:4@amend-2026` | Six months for clerical work and three for others, with a three-month extension for skilled workers: the extension wording needs a person to decide the limit. |
| Wage payment deadline | `BLA-2006:123@amend-2013` | The section counts working days (কর্মদিবস); the fact schema counts days, so calendar and working days would be mixed. |
| Annual leave | `BLA-2006:117@amend-2026` | The entitlement is a ratio (one day per 18 days worked and so on), not a number of days. |

## Known limits of all rules

- No rule knows the worker category (the corpus has no worker-category terms), so exemptions listed above are not applied. A draft rule that flags an exempt worker would be a wrong finding; decide whether to activate such a rule or to leave it as a draft.
- Rules read only facts the contract states. A contract that is silent produces no rule finding; omissions are found by the risk agent.
