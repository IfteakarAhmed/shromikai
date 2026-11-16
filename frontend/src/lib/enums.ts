import type { components } from '../api/types'

/**
 * The values of the API's enums as runtime lists. Each is a record keyed by the generated type, so a
 * value the API adds or drops breaks the type check here instead of showing a stale menu.
 */
type ClauseType = components['schemas']['ClauseType']
type PlaybookRule = components['schemas']['PlaybookRule']
type Unit = NonNullable<PlaybookRule['unit']>

const CLAUSE_TYPE_SET: Record<ClauseType, true> = {
  APPOINTMENT_AND_ROLE: true,
  PROBATION: true,
  WAGES: true,
  WAGE_PAYMENT_AND_DEDUCTIONS: true,
  WORKING_HOURS: true,
  OVERTIME: true,
  REST_AND_HOLIDAYS: true,
  LEAVE: true,
  MATERNITY_BENEFIT: true,
  NOTICE_AND_RESIGNATION: true,
  TERMINATION: true,
  DISCIPLINE_AND_MISCONDUCT: true,
  SERVICE_BENEFITS: true,
  TRANSFER_AND_DEPLOYMENT: true,
  CONFIDENTIALITY: true,
  NON_COMPETE: true,
  INTELLECTUAL_PROPERTY: true,
  HEALTH_AND_SAFETY: true,
  DISPUTE_RESOLUTION: true,
  GOVERNING_LAW: true,
  GENERAL_PROVISIONS: true,
  OTHER: true,
}
const FACT_SET: Record<PlaybookRule['fact'], true> = {
  PROBATION_LENGTH: true,
  PROBATION_EXTENSION: true,
  CONTRACT_DURATION: true,
  NOTICE_BY_EMPLOYER: true,
  NOTICE_BY_WORKER: true,
  PAY_IN_LIEU_OF_NOTICE_ALLOWED: true,
  DAILY_WORKING_HOURS: true,
  WEEKLY_WORKING_HOURS: true,
  OVERTIME_RATE: true,
  OVERTIME_DAILY_HOURS: true,
  OVERTIME_COMPULSORY: true,
  WEEKLY_REST_DAYS: true,
  ANNUAL_LEAVE: true,
  CASUAL_LEAVE: true,
  SICK_LEAVE: true,
  FESTIVAL_HOLIDAYS: true,
  MATERNITY_LEAVE: true,
  WAGE_PERIOD: true,
  WAGE_PAYMENT_DEADLINE: true,
  DEDUCTION_LIMIT: true,
  BASIC_WAGE: true,
  GROSS_WAGE: true,
  TERMINATION_WITHOUT_NOTICE_ALLOWED: true,
  TERMINATION_BENEFIT_STATED: true,
  NON_COMPETE_DURATION: true,
  DISPUTE_FORUM_STATED: true,
}
const UNIT_SET: Record<Unit, true> = {
  DAYS: true,
  WEEKS: true,
  MONTHS: true,
  HOURS_PER_DAY: true,
  HOURS_PER_WEEK: true,
  MULTIPLIER: true,
  PERCENT: true,
  BDT_PER_MONTH: true,
  COUNT: true,
  BOOLEAN: true,
}
const COMPARATOR_SET: Record<PlaybookRule['comparator'], true> = {
  MIN: true,
  MAX: true,
  EQ: true,
  REQUIRED: true,
  PROHIBITED: true,
}
const SEVERITY_SET: Record<PlaybookRule['severity'], true> = {
  CRITICAL: true,
  HIGH: true,
  MEDIUM: true,
  LOW: true,
}

export const CLAUSE_TYPES = Object.keys(CLAUSE_TYPE_SET) as ClauseType[]
export const FACTS = Object.keys(FACT_SET) as PlaybookRule['fact'][]
export const UNITS = Object.keys(UNIT_SET) as Unit[]
export const COMPARATORS = Object.keys(COMPARATOR_SET) as PlaybookRule['comparator'][]
export const SEVERITIES = Object.keys(SEVERITY_SET) as PlaybookRule['severity'][]

/**
 * The units each fact may carry, as the API validates them (api.md §6.4). These say what a contract
 * can state, not what the law requires; no legal limit lives here (R-10).
 */
export const FACT_UNITS: Record<PlaybookRule['fact'], readonly Unit[]> = {
  PROBATION_LENGTH: ['MONTHS', 'DAYS'],
  PROBATION_EXTENSION: ['MONTHS', 'DAYS'],
  CONTRACT_DURATION: ['MONTHS'],
  NOTICE_BY_EMPLOYER: ['DAYS', 'WEEKS'],
  NOTICE_BY_WORKER: ['DAYS', 'WEEKS'],
  PAY_IN_LIEU_OF_NOTICE_ALLOWED: ['BOOLEAN'],
  DAILY_WORKING_HOURS: ['HOURS_PER_DAY'],
  WEEKLY_WORKING_HOURS: ['HOURS_PER_WEEK'],
  OVERTIME_RATE: ['MULTIPLIER'],
  OVERTIME_DAILY_HOURS: ['HOURS_PER_DAY'],
  OVERTIME_COMPULSORY: ['BOOLEAN'],
  WEEKLY_REST_DAYS: ['COUNT'],
  ANNUAL_LEAVE: ['DAYS'],
  CASUAL_LEAVE: ['DAYS'],
  SICK_LEAVE: ['DAYS'],
  FESTIVAL_HOLIDAYS: ['DAYS'],
  MATERNITY_LEAVE: ['DAYS', 'WEEKS'],
  WAGE_PERIOD: ['DAYS'],
  WAGE_PAYMENT_DEADLINE: ['DAYS'],
  DEDUCTION_LIMIT: ['PERCENT'],
  BASIC_WAGE: ['BDT_PER_MONTH'],
  GROSS_WAGE: ['BDT_PER_MONTH'],
  TERMINATION_WITHOUT_NOTICE_ALLOWED: ['BOOLEAN'],
  TERMINATION_BENEFIT_STATED: ['BOOLEAN'],
  NON_COMPETE_DURATION: ['MONTHS'],
  DISPUTE_FORUM_STATED: ['BOOLEAN'],
}
