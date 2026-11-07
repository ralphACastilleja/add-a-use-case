// UC-STU-deactivate-student: UI contract with stubbed action responses. Real JWT/API coverage is backend-tested.
const student = { id: 4, username: 'j.smith@abc.edu', firstName: 'John', lastName: 'Smith', email: 'j.smith@abc.edu', enabled: true, teamId: 1, sectionId: 2 }
const envelope = data => ({ flag: true, code: 200, message: 'Success', data })
describe('Student deactivation', () => {
  let mutations
  beforeEach(() => {
    mutations = 0
    cy.login('b.wei@abc.edu', '123456')
    cy.intercept('GET', '**/api/v1/students/4', { body: envelope(student) }).as('student')
    cy.visit('/students/4')
    cy.wait('@student')
  })
  it('warns about consequences and cancels without a mutation (4a)', () => {
    cy.intercept('POST', '**/students/4/deactivate', req => { mutations++; req.reply(envelope({ ...student, enabled: false })) })
    cy.get('[data-cy=deactivate-open]').click()
    cy.get('[role=dialog]').should('contain', 'existing login').and('contain', 'team membership').and('contain', 'submitted work').and('contain', 'reactivated')
    cy.get('[data-cy=deactivate-cancel]').click()
    cy.get('[data-cy=student-status]').should('contain', 'Active')
    cy.then(() => expect(mutations).to.eq(0))
  })
  it('confirms once, prevents duplicate submission, and refreshes status', () => {
    cy.intercept('POST', '**/students/4/deactivate', req => {
      mutations++
      req.reply({ delay: 800, body: envelope({ ...student, enabled: false }) })
    }).as('deactivate')
    cy.get('[data-cy=deactivate-open]').click()
    cy.intercept('GET', '**/api/v1/students/4', { body: envelope({ ...student, enabled: false }) }).as('refresh')
    cy.get('[data-cy=deactivate-confirm]').click().should('be.disabled')
    cy.wait('@deactivate').its('request.method').should('eq', 'POST')
    cy.wait('@refresh')
    cy.get('[data-cy=student-status]').should('contain', 'Inactive')
    cy.get('[data-cy=deactivate-open]').should('not.exist')
    cy.contains('Student deactivated successfully').should('be.visible')
    cy.then(() => expect(mutations).to.eq(1))
  })
  it('reports a repeated confirmation and refreshes inactive status (6b)', () => {
    cy.intercept('POST', '**/students/4/deactivate', { statusCode: 409, body: { flag: false, code: 409, message: 'This student is already deactivated.' } }).as('conflict')
    cy.get('[data-cy=deactivate-open]').click()
    cy.intercept('GET', '**/api/v1/students/4', { body: envelope({ ...student, enabled: false }) })
    cy.get('[data-cy=deactivate-confirm]').click()
    cy.wait('@conflict')
    cy.contains('This student is already deactivated.').should('be.visible')
    cy.get('[data-cy=student-status]').should('contain', 'Inactive')
  })
  it('does not claim success after a failed request', () => {
    cy.intercept('POST', '**/students/4/deactivate', { statusCode: 500, body: { flag: false, code: 500, message: 'Unable to deactivate student.' } })
    cy.get('[data-cy=deactivate-open]').click()
    cy.get('[data-cy=deactivate-confirm]').click()
    cy.contains('Unable to deactivate student.').should('be.visible')
    cy.get('[data-cy=student-status]').should('contain', 'Active')
  })
})
