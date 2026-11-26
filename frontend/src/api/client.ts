import * as auth from './auth'
import * as admin from './admin'
import * as course from './course'
import * as evaluation from './evaluation'
import * as experiment from './experiment'
import * as flashcards from './flashcards'
import * as knowledge from './knowledge'
import * as notes from './notes'
import * as practice from './practice'
import * as recommendation from './recommendation'
import * as system from './system'
import * as tutor from './tutor'
import { api, apiError, refreshAccessToken, shouldRefreshAfterUnauthorized } from './http'

export { api, apiError, refreshAccessToken, shouldRefreshAfterUnauthorized }
export * from './types'

/**
 * Compatibility facade: existing views keep importing one client while each
 * business domain owns its HTTP contract in a focused module.
 */
export const learningApi = {
  ...auth,
  ...course,
  ...knowledge,
  ...notes,
  ...practice,
  ...evaluation,
  ...experiment,
  ...flashcards,
  ...admin,
  ...recommendation,
  ...system,
  ...tutor,
}
