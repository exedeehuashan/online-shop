import {render,screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {expect,it,vi} from 'vitest';
import css from '../styles.css?raw';
import {Badge,Breadcrumb,Button,Card,DataList,EmptyState,Gallery,Input,Pagination,Price,ProductCard,QuantityStepper,Select,StatusRegion,Tabs,Textarea,Toast,ValidationSummary} from '../ui/primitives';
import {Modal} from '../ui/Modal';

it('publishes semantic commercial tokens and responsive/reduced-motion rules',()=>{
  for(const token of ['--color-brand','--color-success','--color-warning','--color-danger','--color-info','--color-focus','--space-1','--space-8','--radius-md','--shadow-md','--container-max']) expect(css).toContain(token);
  expect(css).toContain('@media (prefers-reduced-motion: reduce)');
  expect(css).toContain('min-height:44px');
  expect(css).toMatch(/@media \(min-width:\s*40rem\)/);
});

it('renders reusable primitives without feature behavior',async()=>{
  const change=vi.fn();
  render(<><Card><Badge tone="success">Available</Badge><Price amount="12.30" currency="NZD"/><Button>Buy</Button></Card><QuantityStepper value={2} onChange={change}/><EmptyState title="Nothing here">Try again later.</EmptyState><StatusRegion message="Saved"/></>);
  expect(document.querySelector('data')).toHaveAttribute('value','NZD 12.30');
  await userEvent.click(screen.getByRole('button',{name:'Increase quantity'}));
  expect(change).toHaveBeenCalledWith(3);
  expect(screen.getByText('Saved')).toHaveAttribute('role','status');
});

it('restores focus to the opener after a modal closes',async()=>{
  function Fixture(){const [open,setOpen]=React.useState(false);return <><button onClick={()=>setOpen(true)}>Open help</button><Modal open={open} title="Help" onClose={()=>setOpen(false)}>Useful details</Modal></>}
  const React=await import('react');
  render(<Fixture/>);
  const opener=screen.getByRole('button',{name:'Open help'});
  await userEvent.click(opener);
  expect(screen.getByRole('dialog')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button',{name:'Close Help'}));
  expect(opener).toHaveFocus();
});

it('contains keyboard focus inside an open modal',async()=>{
  function Fixture(){const [open,setOpen]=React.useState(false);return <><button onClick={()=>setOpen(true)}>Open help</button><Modal open={open} title="Help" onClose={()=>setOpen(false)}><a href="/details">Details</a></Modal></>}
  const React=await import('react');
  render(<Fixture/>);await userEvent.click(screen.getByRole('button',{name:'Open help'}));
  const close=screen.getByRole('button',{name:'Close Help'});const details=screen.getByRole('link',{name:'Details'});
  close.focus();await userEvent.tab({shift:true});expect(details).toHaveFocus();await userEvent.tab();expect(close).toHaveFocus();
});

it('supports roving arrow focus and exposes the active tab panel',async()=>{
  function Fixture(){const [active,setActive]=React.useState('description');return <Tabs label="Details" tabs={[{id:'description',label:'Description',panel:'Product description'},{id:'delivery',label:'Delivery',panel:'Delivery details'}]} active={active} onChange={setActive}/>}
  const React=await import('react');render(<Fixture/>);const description=screen.getByRole('tab',{name:'Description'});description.focus();await userEvent.keyboard('{ArrowRight}');expect(screen.getByRole('tab',{name:'Delivery'})).toHaveFocus();expect(screen.getByRole('tabpanel')).toHaveTextContent('Delivery details');await userEvent.keyboard('{Home}');expect(description).toHaveFocus();
});

it('labels form, navigation, data, feedback, and media primitives accessibly',()=>{
  render(<><Input label="Email" hint="Used for receipts" error="Enter an email"/><Select label="Size"><option>Small</option></Select><Textarea label="Notes"/><ValidationSummary errors={['Enter an email']}/><Breadcrumb items={[{label:'Home',href:'/'},{label:'Product'}]}/><Pagination page={1} pageCount={2} onPageChange={vi.fn()}/><Tabs label="Details" tabs={[{id:'description',label:'Description'}]} active="description" onChange={vi.fn()}/><Toast message="Saved"/><DataList caption="Orders" headers={['Number']} rows={[[<>123</>]]}/><ProductCard name="Jacket" imageUrl="/jacket.jpg" imageAlt="Green jacket" width={640} height={480} price={<Price amount="50" currency="NZD"/>}/><Gallery images={[{src:'/detail.jpg',alt:'Jacket detail',width:640,height:480}]}/></>);
  expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid','true');
  expect(screen.getByRole('navigation',{name:'Breadcrumb'})).toBeInTheDocument();
  expect(screen.getByRole('navigation',{name:'Pagination'})).toBeInTheDocument();
  expect(screen.getByRole('tab',{name:'Description'})).toHaveAttribute('aria-selected','true');
  expect(screen.getByRole('table',{name:'Orders'})).toBeInTheDocument();
  expect(screen.getByRole('img',{name:'Green jacket'})).toHaveAttribute('width','640');
});
