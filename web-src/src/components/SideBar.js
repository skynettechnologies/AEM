/*
* <license header>
*/

import React from 'react'
import { NavLink } from 'react-router-dom'

function SideBar () {
  return (
    <ul className="SideNav">
      <li className="SideNav-item">
        <NavLink className="SideNav-itemLink" activeClassName="is-selected" aria-current="page" exact to="/">Setting</NavLink>
      </li>

      <li className="SideNav-item">
        <NavLink className="SideNav-itemLink" activeClassName="is-selected" aria-current="page" to="/about">About All in One Accessibility</NavLink>
      </li>
    </ul>
  )
}

export default SideBar
