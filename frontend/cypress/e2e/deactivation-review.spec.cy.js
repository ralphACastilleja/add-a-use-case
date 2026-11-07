const ok = data => ({ flag: true, code: 200, message: 'Success', data })
describe('Deactivation review gaps', () => {
  for (const saved of [false, true]) {
    it(`omits inactive targets from ${saved ? 'saved' : 'new'} evaluation forms`, () => {
      cy.login('j.smith@abc.edu', '123456')
      cy.intercept('GET', '**/api/v1/students/teams/1', { body: ok([
        { id: 4, firstName: 'Active', lastName: 'Target', enabled: true },
        { id: 5, firstName: 'Inactive', lastName: 'Target', enabled: false }
      ]) })
      cy.intercept('GET', '**/api/v1/sections/2', { body: ok({ rubricId: 1 }) })
      cy.intercept('GET', '**/api/v1/rubrics/1', { body: ok({ criteria: [] }) })
      cy.intercept('GET', '**/api/v1/evaluations/evaluators/4/week/*', { body: ok(saved ? [
        { evaluateeId: 4, evaluateeName: 'Active Target', ratings: [] },
        { evaluateeId: 5, evaluateeName: 'Inactive Target', ratings: [] }
      ] : []) })
      cy.visit('/evaluations/submit-evaluations')
      cy.get('.el-table').should('contain', 'Active Target').and('not.contain', 'Inactive Target')
    })
  }
  it('confirms deactivation through the real browser, API and database', () => {
    cy.login('b.wei@abc.edu', '123456')
    cy.intercept('POST', '**/api/v1/students/4/deactivate').as('realAction')
    cy.visit('/students/4')
    cy.get('[data-cy=deactivate-open]').click()
    cy.get('[role=dialog]').should('be.visible')
    cy.screenshot('student-deactivation-confirmation', { capture: 'viewport' })
    cy.get('[data-cy=deactivate-confirm]').click()
    cy.wait('@realAction').its('response.statusCode').should('eq', 200)
    cy.get('[data-cy=student-status]').should('contain', 'Inactive')
    cy.reload()
    cy.get('[data-cy=student-status]').should('contain', 'Inactive')
    cy.get('[data-cy=deactivate-open]').should('not.exist')
  })
})
